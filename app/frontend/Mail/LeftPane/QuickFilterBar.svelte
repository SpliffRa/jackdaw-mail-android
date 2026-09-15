<!-- Outlook-style filter + sort pills above the message list -->
{#if folder}
  <HorizontalScroll edgeButtons bind:this={filterScroll} className="quick-filters-scroll content-fit">
    <hbox class="quick-filters font-smallest">
    {#each visibleDefs as filter (filter.id)}
      {#if filter.kind == "sort"}
        <button type="button"
          class="pill sort sort-menu-trigger"
          class:active={true}
          aria-haspopup="menu"
          aria-expanded={sortMenuOpen}
          aria-pressed={true}
          title={$t`Sort messages`}
          bind:this={sortAnchor}
          on:click|stopPropagation={onSortClick}>
          <span class="pill-label">{currentSortLabel}</span>
          <ChevronDownIcon size="12px" />
        </button>
      {:else}
        <hbox class="filter-pill"
          class:active={isActive(filter.id, $quickSearch, $mailListSort)}>
          <button type="button"
            class="pill filter-trigger"
            class:active={isActive(filter.id, $quickSearch, $mailListSort)}
            aria-pressed={isActive(filter.id, $quickSearch, $mailListSort)}
            title={filter.label()}
            on:click={() => catchErrors(() => toggleFilter(filter.id))}>
            <span class="pill-label">{filter.label()}</span>
          </button>
          <button type="button"
            class="pill-remove"
            aria-label={$t`Remove this filter button`}
            title={$t`Remove this filter button`}
            on:click|stopPropagation={() => catchErrors(() => onRemove(filter.id))}>×</button>
        </hbox>
      {/if}
    {/each}

    <button type="button" class="pill add" title={$t`Add filter`}
      bind:this={addAnchor}
      on:click|stopPropagation={onAddClick}>
      +
    </button>

    <!-- Keep Menu always mounted so the opening click doesn't race with mount+autoClose -->
    <Menu bind:isMenuOpen={addMenuOpen} anchor={addAnchor} placement="bottom-start">
      {#each hiddenDefs as filter (filter.id)}
        <MenuItem
          label={filter.label()}
          onClick={() => onAdd(filter.id)} />
      {/each}
      {#if !hiddenDefs.length}
        <MenuItem
          label={$t`All filters shown`}
          disabled={true}
          onClick={() => {}} />
      {/if}
      <MenuDivider />
      <MenuItem
        label={$t`Reset filters`}
        onClick={onReset} />
    </Menu>

    <Menu bind:isMenuOpen={sortMenuOpen} anchor={sortAnchor} placement="bottom-start">
      {#each sortDefs as sort (sort.id)}
        <MenuItem
          label={sort.label()}
          selected={$mailListSort == sort.sort}
          onClick={() => selectSort(sort.sort)} />
      {/each}
    </Menu>

    {#if anyActive}
      <button type="button" class="pill clear" title={$t`Clear filters`}
        on:click={() => catchErrors(clearFilters)}>
        {$t`Clear`}
      </button>
    {/if}
    </hbox>
  </HorizontalScroll>
{/if}

<script lang="ts">
  import type { Folder } from "../../../logic/Mail/Folder";
  import type { EMail } from "../../../logic/Mail/EMail";
  import { quickSearch } from "../Selected";
  import {
    type QuickFilterId,
    type QuickFilterDef,
    type MailListSort,
    allQuickFilters,
    getVisibleQuickFilters,
    addQuickFilter,
    removeQuickFilter,
    resetQuickFilters,
    mailListSort,
  } from "./quickFilters";
  import Menu from "../../Shared/Menu/Menu.svelte";
  import MenuItem from "../../Shared/Menu/MenuItem.svelte";
  import MenuDivider from "../../Shared/Menu/MenuDivider.svelte";
  import { catchErrors } from "../../Util/error";
  import type { ArrayColl } from "svelte-collections";
  import { t } from "../../../l10n/l10n";
  import ChevronDownIcon from "lucide-svelte/icons/chevron-down";
  import HorizontalScroll from "../../Shared/HorizontalScroll.svelte";

  export let folder: Folder;
  export let searchMessages: ArrayColl<EMail> | null; /** out */

  let filterScroll: HorizontalScroll;

  let visibleIds = getVisibleQuickFilters();
  let addMenuOpen = false;
  let addAnchor: HTMLElement;
  let sortMenuOpen = false;
  let sortAnchor: HTMLElement;

  $: visibleDefs = visibleIds
    .map(id => allQuickFilters.find(f => f.id == id))
    .filter((filter): filter is QuickFilterDef => !!filter);
  $: hiddenDefs = allQuickFilters.filter(f => !visibleIds.includes(f.id));
  $: sortDefs = allQuickFilters.filter(f => f.kind == "sort");
  $: visibleDefs, filterScroll?.refresh();
  $: anyActive, filterScroll?.refresh();
  $: currentSortDef = sortDefs.find(s => s.sort === $mailListSort) ?? sortDefs[0];
  $: currentSortLabel = currentSortDef?.label() ?? $t`Newest`;
  $: anyActive =
    $quickSearch.isRead === false ||
    $quickSearch.isStarred === true ||
    $quickSearch.isImportant === true ||
    $quickSearch.hasAttachment === true ||
    $quickSearch.isOutgoing === true ||
    $quickSearch.isOutgoing === false ||
    $quickSearch.isReplied === true;

  $: localMsgCount = folder?.messages ? $folder.messages.length : 0;
  $: quickSearch.folder = folder;
  $: folder && ($folder.countUnread, $folder.countTotal, $folder.countNewArrived, localMsgCount) &&
    $quickSearch && catchErrors(runSearch);

  function isActive(id: QuickFilterId, search = quickSearch, sort?: MailListSort): boolean {
    switch (id) {
      case "unread": return search.isRead === false;
      case "starred": return search.isStarred === true;
      case "important": return search.isImportant === true;
      case "attachments": return search.hasAttachment === true;
      case "fromMe": return search.isOutgoing === true;
      case "toMe": return search.isOutgoing === false;
      case "replied": return search.isReplied === true;
      case "newest": return sort === "date-desc";
      case "oldest": return sort === "date-asc";
      case "bySender": return sort === "sender";
      case "bySubject": return sort === "subject";
    }
  }

  function toggleFilter(id: QuickFilterId) {
    let def = allQuickFilters.find(f => f.id == id);
    if (def?.kind == "sort" && def.sort) {
      mailListSort.set(def.sort);
      return;
    }
    switch (id) {
      case "unread":
        quickSearch.isRead = quickSearch.isRead === false ? null : false;
        break;
      case "starred":
        quickSearch.isStarred = quickSearch.isStarred ? null : true;
        break;
      case "important":
        quickSearch.isImportant = quickSearch.isImportant ? null : true;
        break;
      case "attachments":
        quickSearch.hasAttachment = quickSearch.hasAttachment ? null : true;
        break;
      case "fromMe":
        quickSearch.isOutgoing = quickSearch.isOutgoing === true ? null : true;
        break;
      case "toMe":
        quickSearch.isOutgoing = quickSearch.isOutgoing === false ? null : false;
        break;
      case "replied":
        quickSearch.isReplied = quickSearch.isReplied ? null : true;
        break;
    }
  }

  function onSortClick() {
    setTimeout(() => {
      sortMenuOpen = !sortMenuOpen;
    }, 0);
  }

  function selectSort(sort: MailListSort) {
    mailListSort.set(sort);
    sortMenuOpen = false;
  }

  function clearFilters() {
    quickSearch.isRead = null;
    quickSearch.isStarred = null;
    quickSearch.isImportant = null;
    quickSearch.hasAttachment = null;
    quickSearch.isOutgoing = null;
    quickSearch.isReplied = null;
  }

  async function runSearch() {
    searchMessages = await quickSearch.startSearch();
  }

  function refreshVisible() {
    visibleIds = getVisibleQuickFilters();
  }

  function onAdd(id: QuickFilterId) {
    addQuickFilter(id);
    refreshVisible();
    addMenuOpen = false;
  }

  function onRemove(id: QuickFilterId) {
    if (isActive(id) && allQuickFilters.find(f => f.id == id)?.kind == "filter") {
      toggleFilter(id);
    }
    removeQuickFilter(id);
    refreshVisible();
  }

  function onAddClick(event: MouseEvent) {
    event.preventDefault();
    event.stopPropagation();
    // Defer open so the same click doesn't hit Popup's window autoClose
    setTimeout(() => {
      addMenuOpen = !addMenuOpen;
    }, 0);
  }

  function onReset() {
    resetQuickFilters();
    refreshVisible();
    addMenuOpen = false;
  }
</script>

<style>
  :global(.quick-filters-scroll) {
    flex: 1 1 auto;
    min-width: 0;
    max-width: 100%;
  }
  .quick-filters {
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    flex-wrap: nowrap;
    min-height: 36px;
    box-sizing: border-box;
    background-color: var(--main-bg);
    border-block-end: 1px solid var(--border);
  }
  .pill {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: 1px solid var(--border);
    background-color: transparent;
    color: var(--main-fg);
    border-radius: var(--border-radius);
    padding: 3px 10px;
    font: inherit;
    font-size: 11px;
    letter-spacing: -0.01em;
    cursor: default;
    line-height: 1.2;
    max-width: 14em;
  }
  .filter-pill {
    display: inline-flex;
    align-items: center;
    max-width: 14em;
    border: 1px solid var(--border);
    border-radius: var(--border-radius);
    background-color: transparent;
    color: var(--main-fg);
    overflow: hidden;
  }
  .filter-pill.active {
    background-color: var(--selected-bg);
    color: var(--selected-fg);
    border-color: transparent;
  }
  .filter-trigger {
    min-width: 0;
    max-width: none;
    border: 0;
    background: transparent;
    color: inherit;
    border-radius: 0;
    padding-inline-end: 4px;
  }
  .filter-trigger:hover {
    background-color: transparent;
    color: inherit;
  }
  .filter-trigger:focus-visible {
    outline-offset: -2px;
  }
  .pill:hover {
    background-color: var(--hover-bg);
    color: var(--hover-fg);
  }
  .pill.active {
    background-color: var(--selected-bg);
    color: var(--selected-fg);
    border-color: transparent;
  }
  .pill.sort:not(.active) {
    border-style: dashed;
  }
  .sort-menu-trigger {
    cursor: default;
  }
  .sort-menu-trigger :global(svg) {
    flex-shrink: 0;
  }
  .pill-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .pill-remove {
    flex: 0 0 auto;
    opacity: 0;
    font-size: 14px;
    line-height: 1;
    border: 0;
    border-radius: 0;
    padding: 3px 8px 3px 2px;
    background: transparent;
    color: inherit;
    font: inherit;
    cursor: pointer;
  }
  .filter-pill:hover .pill-remove,
  .filter-pill.active .pill-remove,
  .pill-remove:focus-visible {
    opacity: 0.55;
  }
  .pill-remove:hover {
    opacity: 1 !important;
  }
  .pill.add {
    min-width: 28px;
    justify-content: center;
    padding-inline: 8px;
    font-size: 14px;
    opacity: 0.7;
  }
  .pill.clear {
    border-style: dashed;
    opacity: 0.75;
  }
</style>
