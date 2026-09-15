// @vitest-environment happy-dom
import { describe, expect, test, beforeEach } from "vitest";
import {
  Tag,
  availableTags,
  compareTags,
  getTagByName,
  loadTagsList,
  moveTag,
  reorderTag,
  sortedTagList,
  syncTagsFromMasterCategoryList,
} from "../../../logic/Abstract/Tag";

describe("compareTags", () => {
  function tag(name: string, sortOrder: number | null = null): Tag {
    let t = new Tag();
    t.name = name;
    t.sortOrder = sortOrder;
    return t;
  }

  test("сортирует по sortOrder как Master Category List в Outlook", () => {
    let tags = [
      tag("2. Тестирование", 1),
      tag("1. Ошибка", 0),
      tag("3. Массовая", 2),
    ];
    expect(sortedTagList(tags).map(t => t.name)).toEqual([
      "1. Ошибка",
      "2. Тестирование",
      "3. Массовая",
    ]);
  });

  test("без sortOrder использует естественную сортировку по имени", () => {
    let tags = [
      tag("2. Б"),
      tag("10. А"),
      tag("1. А"),
    ];
    expect(sortedTagList(tags).map(t => t.name)).toEqual([
      "1. А",
      "2. Б",
      "10. А",
    ]);
  });

  test("явный sortOrder имеет приоритет над именем", () => {
    expect(compareTags(tag("Ящик B", 0), tag("Ящик A", 1))).toBeLessThan(0);
  });

  describe("moveTag", () => {
    beforeEach(() => {
      availableTags.clear();
      const store = new Map<string, string>();
      globalThis.localStorage = {
        getItem: key => store.get(key) ?? null,
        setItem: (key, value) => { store.set(key, value); },
        removeItem: key => { store.delete(key); },
        clear: () => { store.clear(); },
        key: () => null,
        length: store.size,
      } as Storage;
    });

    test("меняет порядок при одинаковом sortOrder", async () => {
      let testing = tag("2. Тестирование", 0);
      let service = tag("1. ОБСЛУЖИВАНИЕ", 0);
      let mass = tag("3. Массовая", 0);
      availableTags.addAll([testing, service, mass]);
      expect(sortedTagList().map(t => t.name)).toEqual([
        "1. ОБСЛУЖИВАНИЕ",
        "2. Тестирование",
        "3. Массовая",
      ]);
      await moveTag(testing, -1);
      expect(sortedTagList().map(t => t.name)).toEqual([
        "2. Тестирование",
        "1. ОБСЛУЖИВАНИЕ",
        "3. Массовая",
      ]);
      expect(sortedTagList().map(t => t.sortOrder)).toEqual([0, 1, 2]);
    });

    test("move down сдвигает категорию на одну позицию", async () => {
      let tags = [
        tag("A", 0),
        tag("B", 1),
        tag("C", 2),
      ];
      availableTags.addAll(tags);
      await moveTag(tags[0], 1);
      expect(sortedTagList().map(t => t.name)).toEqual(["B", "A", "C"]);
    });

    test("reorderTag вставляет категорию перед целевой", async () => {
      let tags = [
        tag("A", 0),
        tag("B", 1),
        tag("C", 2),
        tag("D", 3),
      ];
      availableTags.addAll(tags);
      await reorderTag(tags[3], tags[1], "before");
      expect(sortedTagList().map(t => t.name)).toEqual(["A", "D", "B", "C"]);
    });

    test("reorderTag вставляет категорию после целевой", async () => {
      let tags = [
        tag("A", 0),
        tag("B", 1),
        tag("C", 2),
      ];
      availableTags.addAll(tags);
      await reorderTag(tags[0], tags[1], "after");
      expect(sortedTagList().map(t => t.name)).toEqual(["B", "A", "C"]);
    });
  });

  describe("syncTagsFromMasterCategoryList", () => {
    beforeEach(() => {
      availableTags.clear();
      const store = new Map<string, string>();
      globalThis.localStorage = {
        getItem: key => store.get(key) ?? null,
        setItem: (key, value) => { store.set(key, value); },
        removeItem: key => { store.delete(key); },
        clear: () => { store.clear(); },
        key: () => null,
        length: store.size,
      } as Storage;
    });

    test("сохраняет локальный порядок для существующих категорий", async () => {
      availableTags.addAll([
        tag("2. Б", 0),
        tag("1. А", 1),
        tag("Устаревшая", 2),
      ]);
      let result = await syncTagsFromMasterCategoryList([
        { name: "1. А", color: "#ED616F", sortOrder: 0 },
        { name: "2. Б", color: "#0078D7", sortOrder: 1 },
      ], { removeOthers: true });
      expect(sortedTagList().map(t => t.name)).toEqual(["2. Б", "1. А"]);
      expect(result.removed).toBe(1);
      expect(result.added).toBe(0);
    });

    test("добавляет новые категории с ящика в конец", async () => {
      availableTags.add(tag("1. А", 0));
      await syncTagsFromMasterCategoryList([
        { name: "1. А", color: "#ED616F", sortOrder: 0 },
        { name: "2. Б", color: "#0078D7", sortOrder: 1 },
        { name: "3. В", color: "#56A659", sortOrder: 2 },
      ], { removeOthers: false });
      expect(sortedTagList().map(t => t.name)).toEqual(["1. А", "2. Б", "3. В"]);
    });

    test("обновляет цвет с сервера", async () => {
      let existing = tag("1. А", 0);
      existing.color = "#000000";
      availableTags.add(existing);
      await syncTagsFromMasterCategoryList([
        { name: "1. А", color: "#ED616F", sortOrder: 0 },
      ], { removeOthers: false });
      expect(existing.color).toBe("#ED616F");
    });
  });
});

describe("канонизация категорий", () => {
  beforeEach(() => {
    availableTags.clear();
    const store = new Map<string, string>();
    globalThis.localStorage = {
      getItem: key => store.get(key) ?? null,
      setItem: (key, value) => { store.set(key, value); },
      removeItem: key => { store.delete(key); },
      clear: () => { store.clear(); },
      key: () => null,
      length: store.size,
    } as Storage;
  });

  test("повторно использует объект категории после ее удаления из списка", () => {
    let name = `Encrypt-${crypto.randomUUID()}`;
    let original = getTagByName(name);
    let messageTags = new Set([original]);

    availableTags.remove(original);
    let reloaded = getTagByName(name);
    messageTags.add(reloaded);

    expect(reloaded).toBe(original);
    expect(messageTags.size).toBe(1);
  });

  test("удаляет дубликаты категорий из локального хранилища", async () => {
    let name = `duplicate-${crypto.randomUUID()}`;
    localStorage.setItem("tags", JSON.stringify([
      { name, color: "#111111", sortOrder: 0 },
      { name, color: "#222222", sortOrder: 1 },
      { name: `${name}-other`, color: "#333333", sortOrder: 2 },
    ]));

    await loadTagsList();

    expect(sortedTagList().map(tag => tag.name)).toEqual([name, `${name}-other`]);
    expect(JSON.parse(localStorage.getItem("tags")!).map((tag: { name: string }) => tag.name))
      .toEqual([name, `${name}-other`]);
  });
});
