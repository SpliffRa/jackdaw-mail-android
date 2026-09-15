import type { EMail } from "./EMail";
import type { Folder } from "./Folder";
import type { MailAccount } from "./MailAccount";
// #if [!WEBMAIL]
import { getDatabase } from "./SQL/SQLDatabase";
import { SQLEMail } from "./SQL/SQLEMail";
import sql, { type Query } from "../../../lib/rs-sqlite";
// #endif

const kCandidateLimit = 800;
const kResultLimit = kCandidateLimit;
const kContextWindowDays = 365;
const kIndexedTextLength = 6000;
const kMinimumDuplicateBodyLength = 24;
const kMinimumTopicSubjectLength = 8;
const kMinimumSimilarSubjectTokens = 2;
const kMinimumSimilarBodyTokens = 4;
const kSimilarTopicThreshold = 0.42;

const kStopWords = new Set([
  "а", "без", "бы", "в", "вам", "вас", "ведь", "во", "вот", "вы",
  "да", "для", "до", "его", "ее", "если", "есть", "же", "за", "из",
  "или", "им", "их", "как", "когда", "кто", "мне", "мы", "на", "над",
  "нам", "нас", "не", "нет", "ни", "но", "о", "об", "один", "от", "по",
  "под", "при", "про", "с", "со", "так", "такой", "там", "те", "тем", "то",
  "только", "у", "уже", "что", "это", "я", "about", "after", "all", "and",
  "are", "can", "for", "from", "has", "have", "how", "in", "is", "it", "of",
  "on", "or", "that", "the", "this", "to", "was", "we", "with", "you",
]);

export type RelatedMailReason =
  | "duplicate"
  | "same-thread"
  | "same-identifier"
  | "same-topic"
  | "similar-topic";

export interface RelatedMailText {
  messageID?: string | null;
  inReplyTo?: string | null;
  threadID?: string | null;
  references?: string[] | null;
  subject?: string | null;
  body?: string | null;
  contactEmail?: string | null;
  sentAt?: number | null;
}

export interface RelatedMailSignal {
  reason: RelatedMailReason;
  score: number;
  sharedIdentifiers: string[];
}

export interface RelatedMailMatch {
  email: EMail;
  reason: RelatedMailReason;
  score: number;
  sharedIdentifiers: string[];
}

export type RelatedMailGroupKind = "identifier" | "conversation" | "topic";

export interface RelatedMailGroup {
  key: string;
  kind: RelatedMailGroupKind;
  label: string;
  matches: RelatedMailMatch[];
}

/** Группирует найденные письма по самому полезному общему признаку. */
export function groupRelatedMailMatches(matches: RelatedMailMatch[]): RelatedMailGroup[] {
  const groups = new Map<string, RelatedMailGroup>();
  for (const match of matches) {
    const subject = compactRelatedSubject(match.email.subject);
    const identifier = match.sharedIdentifiers[0];
    const threadID = match.email.threadID;
    let kind: RelatedMailGroupKind;
    let label: string;
    let key: string;
    if (identifier) {
      kind = "identifier";
      label = identifier;
      key = `identifier:${identifier}`;
    } else if (match.reason == "same-thread" && threadID) {
      kind = "conversation";
      label = subject;
      key = `conversation:${threadID}`;
    } else {
      kind = "topic";
      label = subject;
      key = `topic:${normalizeRelatedSubject(subject) || "no-subject"}`;
    }
    let group = groups.get(key);
    if (!group) {
      group = { key, kind, label, matches: [] };
      groups.set(key, group);
    }
    group.matches.push(match);
  }
  return [...groups.values()];
}

/** Removes reply/forward prefixes while preserving the actual subject. */
export function normalizeRelatedSubject(subject: string | null | undefined): string {
  return (subject ?? "")
    .normalize("NFKC")
    .replace(/^(?:(?:re|fw|fwd|aw|ответ|пересылка)\s*:\s*)+/iu, "")
    .replace(/\s+/gu, " ")
    .trim()
    .toLocaleLowerCase();
}

function compactRelatedSubject(subject: string | null | undefined): string {
  return (subject ?? "").normalize("NFKC").replace(/\s+/gu, " ").trim();
}

/**
 * Extracts conservative request/order/case identifiers.
 * Bare numbers are intentionally ignored to avoid grouping unrelated dates,
 * invoice totals, and phone numbers.
 */
export function extractRelatedIdentifiers(text: string | null | undefined): string[] {
  if (!text) {
    return [];
  }
  const patterns = [
    /#[\p{L}\p{N}][\p{L}\p{N}_/-]{2,39}/gu,
    /\b[\p{L}]{1,8}[-_/][\p{N}]{3,12}\b/giu,
    /\b[\p{L}]{2,8}[\p{N}]{4,12}\b/giu,
  ];
  const identifiers = new Set<string>();
  for (const pattern of patterns) {
    for (const match of text.match(pattern) ?? []) {
      identifiers.add(match.toLocaleLowerCase());
    }
  }
  return [...identifiers];
}

/** Pure classifier kept separate from storage and UI for deterministic tests. */
export function classifyRelatedMail(
  source: RelatedMailText,
  candidate: RelatedMailText,
): RelatedMailSignal | null {
  const sourceSubject = normalizeRelatedSubject(source.subject);
  const candidateSubject = normalizeRelatedSubject(candidate.subject);
  const sourceBody = normalizeRelatedBody(source.body);
  const candidateBody = normalizeRelatedBody(candidate.body);
  const sourceSubjectTokens = relatedTextTokens(sourceSubject);
  const candidateSubjectTokens = relatedTextTokens(candidateSubject);
  const sourceBodyTokens = relatedTextTokens(sourceBody);
  const candidateBodyTokens = relatedTextTokens(candidateBody);
  const sourceIDs = relatedMessageIDs(source);
  const candidateIDs = relatedMessageIDs(candidate);
  const sameThread = !!source.threadID && source.threadID == candidate.threadID;
  const sameReference = [...sourceIDs].some(id => candidateIDs.has(id));
  const sourceIdentifiers = new Set([
    ...extractRelatedIdentifiers(source.subject),
    ...extractRelatedIdentifiers(source.body),
  ]);
  const candidateIdentifiers = new Set([
    ...extractRelatedIdentifiers(candidate.subject),
    ...extractRelatedIdentifiers(candidate.body),
  ]);
  const sharedIdentifiers = [...sourceIdentifiers].filter(id => candidateIdentifiers.has(id));
  const sameTopic = sourceSubject.length >= kMinimumTopicSubjectLength &&
    sourceSubject == candidateSubject &&
    sourceSubjectTokens.size >= kMinimumSimilarSubjectTokens;
  const duplicate = sameTopic &&
    sourceBody.length >= kMinimumDuplicateBodyLength &&
    sourceBody == candidateBody;

  if (duplicate) {
    return { reason: "duplicate", score: 1, sharedIdentifiers };
  }
  if (sameThread) {
    return { reason: "same-thread", score: 0.98, sharedIdentifiers };
  }
  if (sameReference) {
    return { reason: "same-identifier", score: 0.96, sharedIdentifiers };
  }
  if (sharedIdentifiers.length) {
    return { reason: "same-identifier", score: 0.9, sharedIdentifiers };
  }
  if (sameTopic) {
    return { reason: "same-topic", score: 0.82, sharedIdentifiers };
  }

  const subjectSimilarity = jaccardSimilarity(sourceSubjectTokens, candidateSubjectTokens);
  const bodySimilarity = jaccardSimilarity(sourceBodyTokens, candidateBodyTokens);
  const combinedSimilarity = subjectSimilarity * 0.7 + bodySimilarity * 0.3;
  const hasEnoughSubjectOverlap = subjectSimilarity >= 0.35 &&
    sourceSubjectTokens.size >= kMinimumSimilarSubjectTokens;
  const hasEnoughBodyOverlap = bodySimilarity >= 0.5 &&
    sourceBodyTokens.size >= kMinimumSimilarBodyTokens;
  if (combinedSimilarity >= kSimilarTopicThreshold &&
      (hasEnoughSubjectOverlap || hasEnoughBodyOverlap)) {
    return { reason: "similar-topic", score: combinedSimilarity, sharedIdentifiers };
  }
  return null;
}

/** Finds related messages without downloading any new message body. */
export async function findRelatedEmails(
  message: EMail,
  limit = kResultLimit,
): Promise<RelatedMailMatch[]> {
  const account = message.folder?.account;
  if (!account) {
    return [];
  }
  const requestedLimit = Number.isFinite(limit) ? Math.trunc(limit) : kResultLimit;
  const maxResults = Math.max(1, Math.min(kResultLimit, requestedLimit));

  // #if [!WEBMAIL]
  if (account.dbID && message.dbID) {
    return (await findRelatedFromDatabase(message, account, maxResults)).slice(0, maxResults);
  }
  // #endif

  return findRelatedFromLoadedMessages(message, maxResults);
}

function findRelatedFromLoadedMessages(message: EMail, limit: number): RelatedMailMatch[] {
  const source = textFromEmail(message);
  const matches: RelatedMailMatch[] = [];
  const seen = new Set<EMail>();
  const folders = message.folder?.account?.getAllFolders() ?? [];
  candidateLoop:
  for (const folder of folders) {
    for (const candidate of folder.messages) {
      if (candidate == message || seen.has(candidate)) {
        continue;
      }
      seen.add(candidate);
      const signal = classifyRelatedMail(source, textFromEmail(candidate));
      if (signal) {
        matches.push({ email: candidate, ...signal });
      }
      if (seen.size >= kCandidateLimit) {
        break candidateLoop;
      }
    }
  }
  return sortRelatedMatches(matches).slice(0, limit);
}

// #if [!WEBMAIL]
type RelatedMailRow = RelatedMailText & {
  id: number;
  folderID: number;
  pID?: string | number | null;
  dateSent?: number | null;
  dateReceived?: number | null;
  outgoing?: number | null;
  contactName?: string | null;
  isRead?: number | null;
  isStarred?: number | null;
  isReplied?: number | null;
  isForwarded?: number | null;
  isImportant?: number | null;
  isSpam?: number | null;
  isDraft?: number | null;
  downloadComplete?: number | null;
  json?: string | null;
};

async function findRelatedFromDatabase(
  message: EMail,
  account: MailAccount,
  limit: number,
): Promise<RelatedMailMatch[]> {
  const database = await getDatabase();
  const sourceRow = await database.get<RelatedMailRow>(sql`
    SELECT
      e.id as id, e.folderID as folderID,
      e.messageID as messageID, e.parentMsgID as parentMsgID,
      e.threadID as threadID, e.dateSent as dateSent,
      e.dateReceived as dateReceived, e.outgoing as outgoing,
      e.contactEmail as contactEmail, e.contactName as contactName,
      e.subject as subject, substr(e.plaintext, 1, ${kIndexedTextLength}) as body,
      e.isRead as isRead, e.isStarred as isStarred,
      e.isReplied as isReplied, e.isForwarded as isForwarded,
      e.isImportant as isImportant, e.isSpam as isSpam,
      e.isDraft as isDraft, e.downloadComplete as downloadComplete,
      e.json as json
    FROM email e
      JOIN folder f ON f.id = e.folderID
    WHERE e.id = ${message.dbID} AND f.accountID = ${account.dbID}
    LIMIT 1
  `);
  if (!sourceRow) {
    return findRelatedFromLoadedMessages(message, limit);
  }

  const source = textFromRow(sourceRow, message);
  const rows = await findCandidateRows(database, source, account.dbID, message.dbID);
  const folders = new Map<string, Folder>();
  for (const folder of account.getAllFolders()) {
    if (folder.dbID != null) {
      folders.set(String(folder.dbID), folder);
    }
  }

  const matches: RelatedMailMatch[] = [];
  for (const row of rows) {
    const signal = classifyRelatedMail(source, textFromRow(row));
    if (!signal) {
      continue;
    }
    const folder = folders.get(String(row.folderID));
    if (!folder) {
      continue;
    }
    const email = folder.messages.find(candidate => candidate.dbID == row.id);
    if (email) {
      matches.push({ email, ...signal });
      continue;
    }
    const relatedEmail = folder.newEMail();
    await SQLEMail.readMainProperties(row.id, relatedEmail, row);
    matches.push({ email: relatedEmail, ...signal });
  }
  return sortRelatedMatches(matches).slice(0, limit);
}

async function findCandidateRows(
  database: Awaited<ReturnType<typeof getDatabase>>,
  source: RelatedMailText,
  accountID: number | string,
  currentDBID: number | string,
): Promise<RelatedMailRow[]> {
  const sourceTime = Number.isFinite(source.sentAt) ? source.sentAt! : Date.now();
  const windowMS = kContextWindowDays * 24 * 60 * 60 * 1000;
  const windowStart = Math.floor((sourceTime - windowMS) / 1000);
  const windowEnd = Math.ceil((sourceTime + windowMS) / 1000);
  const relationClauses: Query[] = [];
  if (source.threadID) {
    relationClauses.push(sql`e.threadID = ${source.threadID}`);
  }
  const referenceIDs = uniqueStrings([
    source.messageID,
    source.inReplyTo,
    ...(source.references ?? []),
  ]);
  if (referenceIDs.length) {
    relationClauses.push(sql`(
      e.messageID IN ${referenceIDs} OR e.parentMsgID IN ${referenceIDs}
    )`);
  }
  const subjectVariants = relatedSubjectVariants(source.subject);
  if (subjectVariants.length) {
    relationClauses.push(sql`e.subject IN ${subjectVariants}`);
  }
  const relationQuery = combineOr(relationClauses);
  const recentQuery = selectCandidateRows(
    sql`e.dateSent BETWEEN ${windowStart} AND ${windowEnd}`,
    accountID,
    currentDBID,
    1,
  );
  if (!relationQuery) {
    return await database.all<RelatedMailRow>(sql`
      $${recentQuery}
      ORDER BY dateSent DESC
      LIMIT ${kCandidateLimit}
    `);
  }
  const relationRows = selectCandidateRows(
    relationQuery,
    accountID,
    currentDBID,
    0,
  );
  const union = sql`$${recentQuery} UNION ALL $${relationRows}`;
  return await database.all<RelatedMailRow>(sql`
    SELECT
      id, folderID, pID, messageID, parentMsgID, threadID,
      dateSent, dateReceived, outgoing, contactEmail, contactName,
      subject, body, isRead, isStarred, isReplied, isForwarded,
      isImportant, isSpam, isDraft, downloadComplete, json,
      MIN(relationPriority) as relationPriority
    FROM ($${union})
    GROUP BY id
    ORDER BY relationPriority ASC, dateSent DESC
    LIMIT ${kCandidateLimit}
  `);
}

function selectCandidateRows(
  where: Query,
  accountID: number | string,
  currentDBID: number | string,
  relationPriority: number,
): Query {
  return sql`
    SELECT
      e.id as id, e.folderID as folderID, e.pID as pID,
      e.messageID as messageID, e.parentMsgID as parentMsgID,
      e.threadID as threadID, e.dateSent as dateSent,
      e.dateReceived as dateReceived, e.outgoing as outgoing,
      e.contactEmail as contactEmail, e.contactName as contactName,
      e.subject as subject, substr(e.plaintext, 1, ${kIndexedTextLength}) as body,
      e.isRead as isRead, e.isStarred as isStarred,
      e.isReplied as isReplied, e.isForwarded as isForwarded,
      e.isImportant as isImportant, e.isSpam as isSpam,
      e.isDraft as isDraft, e.downloadComplete as downloadComplete,
      e.json as json, ${relationPriority} as relationPriority
    FROM email e
      JOIN folder f ON f.id = e.folderID
    WHERE f.accountID = ${accountID}
      AND e.id != ${currentDBID}
      AND ($${where})
  `;
}

function combineOr(queries: Query[]): Query | null {
  let result: Query | null = null;
  for (const query of queries) {
    result = result ? sql`($${result}) OR ($${query})` : query;
  }
  return result;
}

function textFromRow(row: RelatedMailRow, fallback?: EMail): RelatedMailText {
  return {
    messageID: row.messageID ?? fallback?.messageID ?? null,
    inReplyTo: row.parentMsgID ?? fallback?.inReplyTo ?? null,
    threadID: row.threadID ?? fallback?.threadID ?? null,
    references: fallback?.references ?? null,
    subject: row.subject ?? fallback?.subject ?? "",
    body: row.body ?? fallback?.rawText ?? (fallback?.loadedBody ? fallback.text : ""),
    contactEmail: row.contactEmail ?? fallback?.contact?.emailAddress ?? null,
    sentAt: typeof row.dateSent == "number" ? row.dateSent * 1000 : fallback?.sent?.getTime(),
  };
}
// #endif

function textFromEmail(email: EMail): RelatedMailText {
  return {
    messageID: email.messageID,
    inReplyTo: email.inReplyTo,
    threadID: email.threadID,
    references: email.references,
    subject: email.subject,
    body: email.rawText ?? (email.loadedBody ? email.text : ""),
    contactEmail: email.contact?.emailAddress ?? null,
    sentAt: email.sent?.getTime(),
  };
}

function relatedMessageIDs(message: RelatedMailText): Set<string> {
  return new Set(uniqueStrings([
    message.messageID,
    message.inReplyTo,
    ...(message.references ?? []),
  ]));
}

function normalizeRelatedBody(body: string | null | undefined): string {
  const lines = (body ?? "").split(/\r?\n/u);
  const visibleLines: string[] = [];
  for (const line of lines) {
    if (/^\s*>/u.test(line)) {
      continue;
    }
    if (/^\s*--\s*$/u.test(line)) {
      break;
    }
    visibleLines.push(line);
  }
  return visibleLines
    .join(" ")
    .normalize("NFKC")
    .replace(/https?:\/\/\S+/giu, " url ")
    .replace(/\s+/gu, " ")
    .trim()
    .toLocaleLowerCase();
}

function relatedTextTokens(text: string): Set<string> {
  const normalized = text
    .normalize("NFKC")
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .toLocaleLowerCase();
  return new Set(normalized.split(/\s+/u).filter(token =>
    token.length >= 3 && !kStopWords.has(token)));
}

function jaccardSimilarity(left: Set<string>, right: Set<string>): number {
  if (!left.size || !right.size) {
    return 0;
  }
  let intersection = 0;
  for (const token of left) {
    if (right.has(token)) {
      intersection++;
    }
  }
  return intersection / (left.size + right.size - intersection);
}

function uniqueStrings(values: (string | null | undefined)[]): string[] {
  return [...new Set(values.filter((value): value is string => !!value))];
}

function relatedSubjectVariants(subject: string | null | undefined): string[] {
  const raw = (subject ?? "").normalize("NFKC").replace(/\s+/gu, " ").trim();
  if (!raw) {
    return [];
  }
  const base = raw.replace(/^(?:(?:re|fw|fwd|aw|ответ|пересылка)\s*:\s*)+/iu, "").trim();
  const prefixes = ["", "Re: ", "RE: ", "FW: ", "Fwd: ", "AW: ", "Ответ: ", "Пересылка: "];
  return uniqueStrings([
    raw,
    raw.toLocaleLowerCase(),
    raw.toLocaleUpperCase(),
    ...prefixes.flatMap(prefix => [prefix + base, (prefix + base).toLocaleLowerCase()]),
  ]);
}

function sortRelatedMatches(matches: RelatedMailMatch[]): RelatedMailMatch[] {
  return matches.sort((left, right) => {
    const scoreDifference = right.score - left.score;
    if (scoreDifference) {
      return scoreDifference;
    }
    return (right.email.listDisplayDate()?.getTime() ?? 0) -
      (left.email.listDisplayDate()?.getTime() ?? 0);
  });
}
