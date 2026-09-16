<ButtonMenu
  label={$t`Customize toolbar`}
  buttonIcon={SettingsIcon}
  placement="bottom-end"
  dismissOnPointerLeave={false}>
  <hbox class="menu-heading">{$t`Button size`}</hbox>
  <MenuItem
    label={$t`Compact`}
    selected={ribbonSize == "compact"}
    onClick={() => setSize("compact")} />
  <MenuItem
    label={$t`Normal`}
    selected={ribbonSize == "normal"}
    onClick={() => setSize("normal")} />
  <MenuItem
    label={$t`Large`}
    selected={ribbonSize == "large"}
    onClick={() => setSize("large")} />
  <MenuDivider />
  <hbox class="menu-heading">{$t`Position`}</hbox>
  <MenuItem
    label={$t`Align left`}
    selected={ribbonPlacement == "start"}
    onClick={() => setPlacement("start")} />
  <MenuItem
    label={$t`Center`}
    selected={ribbonPlacement == "center"}
    onClick={() => setPlacement("center")} />
  <MenuItem
    label={$t`Align right`}
    selected={ribbonPlacement == "end"}
    onClick={() => setPlacement("end")} />
  <MenuDivider />
  <hbox class="menu-heading">{$t`Button groups`}</hbox>
  {#each visibleGroups as group, index (group)}
    <!-- svelte-ignore a11y-no-static-element-interactions -->
    <hbox class="group-row" class:drop-target={dropTargetGroup == group}
      role="listitem"
      on:dragover|preventDefault={() => onDragOver(group)}
      on:drop|preventDefault={(event) => onDrop(event, group)}
      on:dragleave={() => onDragLeave(group)}>
      <button type="button" class="drag-handle" draggable="true"
        aria-label={$t`Drag to reorder`}
        title={$t`Drag to reorder`}
        on:dragstart={(event) => onDragStart(event, group)}
        on:dragend={clearDrag}>⋮⋮</button>
      <MenuItem
        label={groupLabel(group)}
        icon={EyeIcon}
        onClick={() => toggleGroup(group)}
        closeOnClick={false} />
      <button
        type="button"
        class="arrow-button"
        aria-label={$t`Move up`}
        title={$t`Move up`}
        disabled={index == 0}
        on:click={() => moveGroup(group, "up")}>↑</button>
      <button
        type="button"
        class="arrow-button"
        aria-label={$t`Move down`}
        title={$t`Move down`}
        disabled={index == visibleGroups.length - 1}
        on:click={() => moveGroup(group, "down")}>↓</button>
    </hbox>
  {/each}
  {#each hiddenGroups as group (group)}
    <MenuItem
      label={`${$t`Show`} ${groupLabel(group)}`}
      icon={EyeIcon}
      onClick={() => setRibbonGroupHidden(group, false)} />
  {/each}
  <MenuDivider />
  <MenuItem
    label={$t`Reset toolbar`}
    icon={ResetIcon}
    onClick={resetRibbonPreferences} />
</ButtonMenu>

<script lang="ts">
  import ButtonMenu from "../../Shared/Menu/ButtonMenu.svelte";
  import MenuItem from "../../Shared/Menu/MenuItem.svelte";
  import MenuDivider from "../../Shared/Menu/MenuDivider.svelte";
  import SettingsIcon from "lucide-svelte/icons/settings-2";
  import EyeIcon from "lucide-svelte/icons/eye";
  import ResetIcon from "lucide-svelte/icons/rotate-ccw";
  import { t } from "../../../l10n/l10n";
  import {
    moveRibbonGroup,
    moveRibbonGroupBefore,
    ribbonGroupLabels,
    ribbonPreferences,
    resetRibbonPreferences,
    setRibbonPlacement,
    setRibbonGroupHidden,
    setRibbonSize,
    type RibbonGroupId,
    type RibbonPlacement,
    type RibbonSize,
  } from "./ribbonPreferences";

  export let includeNew = true;

  $: currentRibbonPreferences = $ribbonPreferences;
  $: ribbonSize = currentRibbonPreferences.size;
  $: ribbonPlacement = currentRibbonPreferences.placement;
  let draggedGroup: RibbonGroupId | null = null;
  let dropTargetGroup: RibbonGroupId | null = null;
  $: visibleGroups = currentRibbonPreferences.order
    .filter(group => !currentRibbonPreferences.hidden.includes(group))
    .filter(group => includeNew || group != "new");
  $: hiddenGroups = (Object.keys(ribbonGroupLabels) as RibbonGroupId[])
    .filter(group => (includeNew || group != "new") && currentRibbonPreferences.hidden.includes(group));

  function groupLabel(group: RibbonGroupId): string {
    switch (group) {
      case "new": return $t`New item`;
      case "delete": return $t`Delete`;
      case "reply": return $t`Reply`;
      case "organize": return $t`Organize`;
      case "status": return $t`Message status`;
      case "more": return $t`More`;
    }
  }

  function setSize(size: RibbonSize): void {
    setRibbonSize(size);
  }

  function setPlacement(placement: RibbonPlacement): void {
    setRibbonPlacement(placement);
  }

  function toggleGroup(group: RibbonGroupId): void {
    setRibbonGroupHidden(group, true);
  }

  function moveGroup(group: RibbonGroupId, direction: "up" | "down"): void {
    moveRibbonGroup(group, direction, visibleGroups);
  }

  function onDragStart(event: DragEvent, group: RibbonGroupId): void {
    draggedGroup = group;
    dropTargetGroup = null;
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", group);
    }
  }

  function onDragOver(group: RibbonGroupId): void {
    if (draggedGroup && draggedGroup != group) {
      dropTargetGroup = group;
    }
  }

  function onDragLeave(group: RibbonGroupId): void {
    if (dropTargetGroup == group) {
      dropTargetGroup = null;
    }
  }

  function onDrop(event: DragEvent, group: RibbonGroupId): void {
    const source = (event.dataTransfer?.getData("text/plain") as RibbonGroupId) || draggedGroup;
    if (source && source != group) {
      moveRibbonGroupBefore(source, group);
    }
    clearDrag();
  }

  function clearDrag(): void {
    draggedGroup = null;
    dropTargetGroup = null;
  }
</script>

<style>
  .menu-heading {
    padding: 6px 14px 4px;
    color: color-mix(in srgb, var(--main-fg) 58%, transparent);
    font-size: 11px;
    font-weight: 650;
  }
  .group-row {
    align-items: center;
    border: 1px solid transparent;
    border-radius: 5px;
    padding-inline-start: 2px;
  }
  .group-row.drop-target {
    border-color: var(--toolbar-control-border);
    background: var(--hover-bg);
  }
  .drag-handle {
    width: 22px;
    min-width: 22px;
    align-self: stretch;
    padding: 0;
    border: 0;
    background: transparent;
    color: color-mix(in srgb, var(--main-fg) 58%, transparent);
    font-size: 14px;
    letter-spacing: -3px;
    cursor: grab;
  }
  .drag-handle:active {
    cursor: grabbing;
  }
  .group-row :global(.menuitem) {
    min-width: 12em;
  }
  .arrow-button {
    width: 26px;
    align-self: stretch;
    padding: 0;
    border: 0;
    background: transparent;
    color: inherit;
    cursor: default;
  }
  .arrow-button:hover:not(:disabled) {
    background: var(--hover-bg);
    color: var(--hover-fg);
  }
  .arrow-button:disabled {
    opacity: 0.35;
  }
</style>
