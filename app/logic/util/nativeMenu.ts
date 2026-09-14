/** Команды, общие для нативного меню настольного приложения и рендерера. */
export const nativeMenuActionChannel = "jackdaw:native-menu-action";
export const nativeMenuLabelsChannel = "jackdaw:native-menu-labels";

export type NativeMenuLabels = {
  about: string;
  services: string;
  hide: string;
  hideOthers: string;
  showAll: string;
  quit: string;
  file: string;
  new: string;
  email: string;
  calendarEvent: string;
  contact: string;
  close: string;
  edit: string;
  undo: string;
  redo: string;
  cut: string;
  copy: string;
  paste: string;
  selectAll: string;
  view: string;
  mail: string;
  calendar: string;
  contacts: string;
  files: string;
  chat: string;
  reports: string;
  search: string;
  currentApp: string;
  toggleDevTools: string;
  resetZoom: string;
  zoomIn: string;
  zoomOut: string;
  fullscreen: string;
  message: string;
  reply: string;
  replyAll: string;
  forward: string;
  markReadUnread: string;
  archive: string;
  delete: string;
  tools: string;
  getMail: string;
  settings: string;
  window: string;
  minimize: string;
  zoom: string;
  front: string;
};

export const nativeMenuActions = {
  newEmail: "new-email",
  newEvent: "new-event",
  newContact: "new-contact",
  openMail: "open-mail",
  openCalendar: "open-calendar",
  openContacts: "open-contacts",
  openFiles: "open-files",
  openChat: "open-chat",
  openReports: "open-reports",
  focusSearch: "focus-search",
  searchMail: "search-mail",
  searchCalendar: "search-calendar",
  searchContacts: "search-contacts",
  searchFiles: "search-files",
  searchChat: "search-chat",
  reply: "reply",
  replyAll: "reply-all",
  forward: "forward",
  toggleRead: "toggle-read",
  archive: "archive",
  delete: "delete",
  getMail: "get-mail",
  openSettings: "open-settings",
} as const;

export type NativeMenuAction =
  (typeof nativeMenuActions)[keyof typeof nativeMenuActions];
