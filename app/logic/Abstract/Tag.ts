import { Observable, notifyChangedProperty } from "../util/Observable";
import { SetColl } from "svelte-collections";
import { sanitize } from "../../../lib/util/sanitizeDatatypes";

/**
 * Defines a tag/keyword that can be set on an email.
 *
 * This describes the generic class of a tag.
 * The instance of a tag is represented by `EMail.tags`.
 */
export class Tag extends Observable {
  /** ID of the tag. Usually identical to what is displayed to the user.
   * Must be unique. */
  @notifyChangedProperty
  name: string;
  /** HTML color code for the background color of this tag */
  @notifyChangedProperty
  color: string;
  /** Master Category List order, like Outlook. Lower comes first. */
  @notifyChangedProperty
  sortOrder: number | null = null;
}

export const availableTags = new SetColl<Tag>();
/**
 * Хранит один объект Tag для каждого имени в течение работы приложения.
 *
 * Категория может быть удалена из `availableTags`, пока загруженные письма
 * всё ещё ссылаются на неё. Если создать новый объект при чтении флага
 * сервера, в одном письме окажутся два разных объекта с одним именем, что
 * ломает ключи списков Svelte.
 */
const tagsByName = new Map<string, Tag>();

function findTagByName(name: string): Tag | undefined {
  let existing = tagsByName.get(name);
  if (existing) {
    return existing;
  }
  existing = availableTags.find(tag => tag.name == name);
  if (existing) {
    tagsByName.set(name, existing);
  }
  return existing;
}

/** Outlook-style ordering: explicit list position, then natural name sort. */
export function compareTags(a: Tag, b: Tag): number {
  let aOrder = a.sortOrder;
  let bOrder = b.sortOrder;
  if (Number.isFinite(aOrder) && Number.isFinite(bOrder) && aOrder !== bOrder) {
    return aOrder! - bOrder!;
  }
  if (Number.isFinite(aOrder) && !Number.isFinite(bOrder)) {
    return -1;
  }
  if (!Number.isFinite(aOrder) && Number.isFinite(bOrder)) {
    return 1;
  }
  return a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: "base" });
}

export function sortedTagList(tags: Iterable<Tag> = availableTags.contents): Tag[] {
  let unique = new Map<string, Tag>();
  for (let tag of tags) {
    if (!unique.has(tag.name)) {
      unique.set(tag.name, tag);
    }
  }
  return [...unique.values()].sort(compareTags);
}

function nextTagSortOrder(): number {
  let max = -1;
  for (let tag of availableTags) {
    if (Number.isFinite(tag.sortOrder)) {
      max = Math.max(max, tag.sortOrder!);
    }
  }
  return max + 1;
}

export function getTagByName(name: string, autoAdd = true): Tag {
  let existing = findTagByName(name);
  if (existing) {
    if (autoAdd && !availableTags.contains(existing)) {
      availableTags.add(existing);
    }
    return existing;
  }
  let tag = new Tag();
  tag.name = name;
  tag.color = "#000000";
  tag.sortOrder = nextTagSortOrder();
  tagsByName.set(name, tag);
  if (autoAdd) {
    availableTags.add(tag);
  }
  return tag;
}

/** Добавляет пользовательскую категорию, сохраняя один объект на имя. */
export function addAvailableTag(tag: Tag): Tag {
  let canonical = getTagByName(tag.name, false);
  if (canonical !== tag) {
    canonical.color = tag.color;
  }
  if (!availableTags.contains(canonical)) {
    availableTags.add(canonical);
  }
  return canonical;
}

export async function loadTagsList() {
  let json: any[] = sanitize.array(JSON.parse(sanitize.nonemptystring(localStorage.getItem("tags"), "[]")), []);
  availableTags.clear();
  let names = new Set<string>();
  let normalized = false;
  for (let [index, tagJSON] of json.entries()) {
    let tag = new Tag();
    tag.name = sanitize.label(tagJSON.name);
    if (!tag.name || names.has(tag.name)) {
      normalized = true;
      continue;
    }
    tag.color = sanitize.string(tagJSON.color, "#00FF00");
    let sortOrder = tagJSON.sortOrder;
    tag.sortOrder = typeof sortOrder == "number" && Number.isFinite(sortOrder) ? sortOrder : index;
    let canonical = getTagByName(tag.name, false);
    canonical.color = tag.color;
    canonical.sortOrder = tag.sortOrder;
    availableTags.add(canonical);
    names.add(tag.name);
  }
  assignTagSortOrders(sortedTagList());
  if (normalized) {
    await saveTagsList();
  }
}

export async function saveTagsList() {
  let json = sortedTagList().map(tag => ({
    name: tag.name,
    color: tag.color,
    sortOrder: tag.sortOrder,
  }));
  localStorage.setItem("tags", JSON.stringify(json));
}

export type MasterCategoryEntry = {
  name: string;
  color: string;
  sortOrder: number;
};

export type SyncTagsResult = {
  added: number;
  updated: number;
  removed: number;
  total: number;
};

const kTagsSyncAccountId = "tags.syncAccountId";

export function getTagsSyncAccountId(): string | null {
  return localStorage.getItem(kTagsSyncAccountId);
}

export function setTagsSyncAccountId(accountId: string | null): void {
  if (accountId) {
    localStorage.setItem(kTagsSyncAccountId, accountId);
  } else {
    localStorage.removeItem(kTagsSyncAccountId);
  }
}

/**
 * Apply a mailbox Master Category List.
 * Keeps the current local order for categories that remain; new ones append in server order.
 */
export async function syncTagsFromMasterCategoryList(
  entries: readonly MasterCategoryEntry[],
  options: { removeOthers: boolean } = { removeOthers: true },
): Promise<SyncTagsResult> {
  let serverNames = new Set(entries.map(entry => entry.name));
  let entryByName = new Map(entries.map(entry => [entry.name, entry]));
  let removed = 0;

  if (options.removeOthers) {
    for (let tag of [...availableTags.contents]) {
      if (!serverNames.has(tag.name)) {
        availableTags.remove(tag);
        removed++;
      }
    }
  }

  let ordered = sortedTagList().filter(tag => serverNames.has(tag.name));
  let knownNames = new Set(ordered.map(tag => tag.name));
  let added = 0;
  let updated = 0;

  for (let tag of ordered) {
    let entry = entryByName.get(tag.name)!;
    if (tag.color != entry.color) {
      tag.color = entry.color;
      updated++;
    }
  }

  for (let entry of entries) {
    if (knownNames.has(entry.name)) {
      continue;
    }
    let tag = getTagByName(entry.name);
    tag.color = entry.color;
    ordered.push(tag);
    knownNames.add(entry.name);
    added++;
  }

  assignTagSortOrders(ordered);
  notifyAvailableTagsChanged();
  await saveTagsList();
  return { added, updated, removed, total: ordered.length };
}

/** Assign unique 0-based sortOrder values in display order. */
export function assignTagSortOrders(tags: readonly Tag[]): void {
  for (let [index, tag] of tags.entries()) {
    if (tag.sortOrder !== index) {
      tag.sortOrder = index;
    }
  }
}

/** SetColl notifies Svelte only on add/remove; ping subscribers after in-place reorder. */
function notifyAvailableTagsChanged(): void {
  (availableTags as SetColl<Tag> & { _notifySvelteOfChanges(): void })._notifySvelteOfChanges();
}

export async function moveTag(tag: Tag, direction: -1 | 1): Promise<void> {
  let tags = sortedTagList();
  let index = tags.indexOf(tag);
  if (index < 0) {
    return;
  }
  let swapIndex = index + direction;
  if (swapIndex < 0 || swapIndex >= tags.length) {
    return;
  }
  await reorderTag(tag, tags[swapIndex], direction == -1 ? "before" : "after");
}

/** Move dragged tag before or after another tag in the Master Category List. */
export async function reorderTag(dragged: Tag, target: Tag, place: "before" | "after"): Promise<void> {
  if (dragged === target) {
    return;
  }
  let tags = sortedTagList();
  let fromIndex = tags.indexOf(dragged);
  if (fromIndex < 0) {
    return;
  }
  if (tags.indexOf(target) < 0) {
    return;
  }
  tags.splice(fromIndex, 1);
  let targetIndex = tags.indexOf(target);
  if (targetIndex < 0) {
    return;
  }
  let insertIndex = place == "after" ? targetIndex + 1 : targetIndex;
  tags.splice(insertIndex, 0, dragged);
  assignTagSortOrders(tags);
  notifyAvailableTagsChanged();
  await saveTagsList();
}

export interface TaggableObject {
  readonly tags: SetColl<Tag>;
  addTag(tag: Tag): Promise<void>;
  removeTag(tag: Tag): Promise<void>;
  clearTags?(): Promise<void>;
}
