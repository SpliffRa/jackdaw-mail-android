<vbox class="workspace-selector" role="menu" on:keydown={onKeyDown} bind:this={selectorEl}>
  {#each [null, ...appGlobal.workspaces.each] as workspace}
    <button
      type="button"
      class="workspace"
      style="--workspace-color: {workspace?.color ?? "var(--fg)"}"
      class:selected={workspace == $selectedWorkspace}
      aria-current={workspace == $selectedWorkspace ? "true" : undefined}
      role="menuitem"
      on:click={event => onWorkspaceSelected(workspace, event)}>
      <span class="dot" aria-hidden="true" />
      <span class="name">{workspace?.name ?? $t`All`}</span>
    </button>
  {/each}
</vbox>

<script lang="ts">
  import type { Workspace } from "../../logic/Abstract/Workspace";
  import { selectedWorkspace, selectWorkspace } from "./Selected";
  import { appGlobal } from "../../logic/app";
  import { t } from "../../l10n/l10n";
  import { createEventDispatcher } from "svelte";

  export let open: boolean;
  const dispatch = createEventDispatcher<{ selected: void; close: void }>();
  let selectorEl: HTMLElement;

  export function focusFirst() {
    selectorEl?.querySelector<HTMLButtonElement>("button")?.focus();
  }

  function onWorkspaceSelected(workspace: Workspace | null, event: Event) {
    event.stopPropagation();
    open = false;
    selectWorkspace(workspace);
    dispatch("selected");
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key == "Escape") {
      event.preventDefault();
      event.stopPropagation();
      open = false;
      dispatch("close");
    }
  }
</script>

<style>
  .workspace-selector {
    min-width: 14em;
    padding: 6px;
    background-color: var(--main-bg);
    color: var(--main-fg);
  }
  .workspace {
    width: 100%;
    border: 0;
    display: flex;
    background: transparent;
    color: inherit;
    font: inherit;
    text-align: start;
    cursor: pointer;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 8px;
    min-height: 36px;
    box-sizing: border-box;
  }
  .workspace .name {
    font-size: 13px;
    font-weight: 500;
    letter-spacing: -0.01em;
    line-height: 1.3;
  }
  .workspace .dot {
    background-color: var(--workspace-color);
    min-width: 10px;
    min-height: 10px;
    border-radius: 999px;
    flex-shrink: 0;
  }
  .workspace:hover {
    background-color: var(--hover-bg);
    color: var(--hover-fg);
  }
  .workspace.selected {
    background-color: var(--selected-bg);
    color: var(--selected-fg);
  }
  .workspace.selected:hover {
    background-color: var(--selected-hover-bg);
    color: var(--selected-hover-fg);
  }
  .workspace:focus-visible {
    outline: 2px solid var(--focus-ring);
    outline-offset: -2px;
  }
</style>
