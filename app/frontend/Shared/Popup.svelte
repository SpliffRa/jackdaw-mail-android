{#if popupOpen}
  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <vbox class="popup"
    on:close
    on:click={onClickInside}
    on:wheel={onClickInside}
    on:pointerenter={onPointerEnterSurface}
    on:pointerleave={onPointerLeaveSurface}
    bind:this={popupEl}
    use:portalToBody
    use:popupContent={popupOptions}>
    <slot />
  </vbox>
{/if}
<!-- Do not close on wheel: SubMenu flyouts are portaled outside popupEl,
     so mousewheel-as-outside would dismiss the menu while scrolling categories. -->
<svelte:window on:click={onClickOutside} on:contextmenu|capture={onContextMenuOutside} />

<script lang="ts">
  import { createPopperActions } from 'svelte-popperjs';
  import type { Modifier, Placement } from '@popperjs/core';
  import { onDestroy } from 'svelte';

  /** in/out */
  export let popupOpen: boolean;
  /** Under/above which element the popup window should appear.
   * The popup will not cover this element, but be just above/below it.
   * in */
  export let popupAnchor: HTMLElement | { getBoundingClientRect(): DOMRect };
  /** Where the popup should appear in relation to the anchor.
   * above/below ("top"/"bottom") and left/right ("start"/"end")
   * in */
  export let placement: Placement = "bottom-end";
  /**
   * In which area the popup may appear. Much larger than the anchor.
   * Typically the entire page,  or just a part of it.
   * Should be large enough to contain the entire popup window
   * in any situation, e.g. if the anchor is in the middle of this area.
   * The popup window will be entirely within this area, and
   * be cut off larger.
   * Document element selector, e.g. '.mail-composer-window'
   * in */
  export let boundaryElSel: string;
  export let autoClose: boolean = true;
  /** Popper hides the popup when the anchor sits in overflow:auto (ribbon scroll). */
  export let disableReferenceHide = false;
  /** Close when the pointer leaves the popup, anchor, and any open submenu flyout. */
  export let dismissOnPointerLeave = false;
  export let dismissDelayMs = 350;

  const popupBoundaryPadding = 8;
  const popupBoundary = resolvePopupBoundary(popupAnchor, boundaryElSel);
  const [popupRef, popupContent, getInstance] = createPopperActions({
    placement: placement,
    strategy: 'fixed',
  });
  let fitPopupMaxHeight: number | null = null;
  let fitPopupUpdatePending = false;
  const fitPopupToBoundary: Modifier<"fitPopupToBoundary", { padding: number }> = {
    name: "fitPopupToBoundary",
    enabled: true,
    phase: "beforeMain",
    options: { padding: popupBoundaryPadding },
    fn({ state, options }) {
      const basePlacement = state.placement.split("-")[0];
      if (basePlacement != "top" && basePlacement != "bottom") {
        if (fitPopupMaxHeight != null) {
          fitPopupMaxHeight = null;
          state.elements.popper.style.removeProperty("--popup-max-height");
        }
        return;
      }

      const boundaryRect = popupBoundary && popupBoundary != document.body && popupBoundary != document.documentElement
        ? popupBoundary.getBoundingClientRect()
        : { top: 0, bottom: window.innerHeight };
      const referenceRect = state.elements.reference.getBoundingClientRect();
      // Передаём flip максимум свободного места с обеих сторон, чтобы он
      // выбрал сторону с большим пространством до расчёта скролл-контейнера.
      const availableHeight = Math.max(
        referenceRect.top - boundaryRect.top - options.padding,
        boundaryRect.bottom - referenceRect.bottom - options.padding,
      );
      const nextMaxHeight = Math.max(1, Math.floor(availableHeight));
      const nextMaxHeightValue = `${nextMaxHeight}px`;
      if (
        fitPopupMaxHeight == nextMaxHeight &&
        state.elements.popper.style.getPropertyValue("--popup-max-height") ==
          nextMaxHeightValue
      ) {
        return;
      }
      fitPopupMaxHeight = nextMaxHeight;
      state.elements.popper.style.setProperty(
        "--popup-max-height",
        nextMaxHeightValue,
      );
      if (!fitPopupUpdatePending) {
        fitPopupUpdatePending = true;
        queueMicrotask(() => {
          fitPopupUpdatePending = false;
          void getInstance()?.update();
        });
      }
    },
  };
  const popupOptions = {
    modifiers: [
      {
        name: 'offset',
        options: { offset: [0, 4] },
        preventOverflow: true,
        allow: true,
      },
      {
        name: 'flip',
        options: {
          padding: popupBoundaryPadding,
          boundary: popupBoundary,
          fallbackPlacements: fallbackPopupPlacements(placement),
          fallbackStrategy: "bestFit",
        },
      },
      {
        name: 'preventOverflow',
        options: {
          padding: popupBoundaryPadding,
          boundary: popupBoundary,
          altAxis: true,
        },
      },
      fitPopupToBoundary,
      disableReferenceHide ? { name: 'hide', enabled: false } : { name: 'hide' },
    ],
  };
  let contentObserver: ResizeObserver;
  let leaveTimer: ReturnType<typeof setTimeout> | null = null;
  let anchorLeaveHook: { destroy?(): void } | null = null;
  let anchorElement: HTMLElement | null = null;

  function resolvePopupBoundary(
    anchor: HTMLElement | { getBoundingClientRect(): DOMRect },
    selector: string,
  ): Element | null {
    if (selector == "body") {
      return document.body;
    }
    if (anchor instanceof HTMLElement) {
      const closestBoundary = anchor.closest(selector);
      if (closestBoundary) {
        return closestBoundary;
      }
    }
    return document.querySelector(selector);
  }

  function fallbackPopupPlacements(value: Placement): Placement[] {
    const [basePlacement, variation] = value.split("-");
    if (basePlacement != "top" && basePlacement != "bottom") {
      return [];
    }
    const opposite = basePlacement == "top" ? "bottom" : "top";
    return [
      (variation ? `${opposite}-${variation}` : opposite) as Placement,
    ];
  }

  function clearLeaveTimer() {
    if (leaveTimer) {
      clearTimeout(leaveTimer);
      leaveTimer = null;
    }
  }

  function isInsideMenuSurface(target: EventTarget | null): boolean {
    if (!(target instanceof Node)) {
      return false;
    }
    if (popupEl?.contains(target)) {
      return true;
    }
    if (popupAnchor instanceof Node && popupAnchor.contains(target)) {
      return true;
    }
    if (anchorElement?.contains(target)) {
      return true;
    }
    if (target instanceof Element && target.closest(".submenu-flyout")) {
      return true;
    }
    return false;
  }

  function hasOpenSubmenuFlyout(): boolean {
    return !!document.querySelector(".submenu-flyout");
  }

  function hasOpenSubmenuTrigger(): boolean {
    return !!document.querySelector(".submenu-item.open");
  }

  function scheduleDismissOnLeave() {
    if (!autoClose || !dismissOnPointerLeave || !popupOpen) {
      return;
    }
    clearLeaveTimer();
    let delay = hasOpenSubmenuFlyout() || hasOpenSubmenuTrigger() ? 800 : dismissDelayMs;
    leaveTimer = setTimeout(() => {
      leaveTimer = null;
      if (hasOpenSubmenuFlyout() || hasOpenSubmenuTrigger()) {
        return;
      }
      popupOpen = false;
    }, delay);
  }

  function onPointerEnterSurface() {
    clearLeaveTimer();
  }

  function onPointerLeaveSurface(event: PointerEvent) {
    if (isInsideMenuSurface(event.relatedTarget)) {
      return;
    }
    scheduleDismissOnLeave();
  }

  function onAnchorPointerEnter() {
    clearLeaveTimer();
  }

  function onAnchorPointerLeave(event: PointerEvent) {
    if (!isInsideMenuSurface(event.relatedTarget)) {
      scheduleDismissOnLeave();
    }
  }

  function isPopperReference(anchor: unknown): anchor is HTMLElement | { getBoundingClientRect(): DOMRect } {
    return anchor instanceof HTMLElement ||
      (!!anchor && typeof (anchor as { getBoundingClientRect?: unknown }).getBoundingClientRect === "function");
  }

  function isDomAnchor(anchor: unknown): anchor is HTMLElement {
    return anchor instanceof EventTarget &&
      typeof (anchor as HTMLElement).addEventListener === "function";
  }

  function syncAnchorLeaveListener(anchor: unknown, open: boolean) {
    anchorLeaveHook?.destroy?.();
    anchorLeaveHook = null;
    if (!open || !dismissOnPointerLeave || !isDomAnchor(anchor)) {
      return;
    }
    anchor.addEventListener("pointerenter", onAnchorPointerEnter);
    anchor.addEventListener("pointerleave", onAnchorPointerLeave);
    anchorLeaveHook = {
      destroy() {
        anchor.removeEventListener("pointerenter", onAnchorPointerEnter);
        anchor.removeEventListener("pointerleave", onAnchorPointerLeave);
      },
    };
  }

  $: syncAnchorLeaveListener(popupAnchor, popupOpen && dismissOnPointerLeave);

  // popupRef is not yet defined when use: hook in parent is invoked, so do it manually
  let popupHook: { destroy?(); };
  $: if (isPopperReference(popupAnchor)) {
    anchorElement = popupAnchor instanceof HTMLElement ? popupAnchor : null;
    if (popupRef) {
      popupHook?.destroy?.();
      popupHook = popupRef(popupAnchor);
    }
  }
  $: if (popupOpen && popupHook && isPopperReference(popupAnchor)) {
    void getInstance()?.update();
  }
  onDestroy(() => {
    popupHook?.destroy();
    anchorLeaveHook?.destroy?.();
    clearLeaveTimer();
    contentObserver?.disconnect();
  });

  function onClickOutside(event: Event) {
    if (!autoClose || !popupOpen) {
      return;
    }
    if (anchorElement && event.target instanceof Node && anchorElement.contains(event.target)) {
      return;
    }
    if (isDomAnchor(popupAnchor) && event.target instanceof Node && popupAnchor.contains(event.target)) {
      return;
    }
    // Ignore events that originated inside the popup (or were stopped there).
    if (popupEl && event.target instanceof Node && popupEl.contains(event.target)) {
      return;
    }
    // SubMenu flyouts are portaled to document.body — treat them as inside.
    if (event.target instanceof Element && event.target.closest(".submenu-flyout")) {
      return;
    }
    popupOpen = false;
  }

  /** A context menu is not a click, so a previously open popup would remain
   * visible beside the native/context menu. Close it before the new menu is
   * opened; capture is needed because the target handler stops propagation. */
  function onContextMenuOutside(event: Event) {
    if (!autoClose || !popupOpen) {
      return;
    }
    if (popupEl && event.target instanceof Node && popupEl.contains(event.target)) {
      return;
    }
    if (event.target instanceof Element && event.target.closest(".submenu-flyout")) {
      return;
    }
    popupOpen = false;
  }
  function onClickInside(event: Event) {
    if (!autoClose || !popupOpen) {
      return;
    }
    event.stopPropagation();
  }

  // Re-position popup when popup content changes
  let popupEl: HTMLDivElement;
  $: popupEl && createResizeObserver();
  function createResizeObserver() {
    contentObserver?.disconnect();
    contentObserver = new ResizeObserver(async () => {
      let popper = getInstance();
      await popper?.update();
    });
    contentObserver.observe(popupEl);
  }

  /** Popper uses fixed positioning; portaling avoids clipping by glass panels. */
  function portalToBody(node: HTMLElement) {
    document.body.appendChild(node);
    return {
      destroy() {
        node.remove();
      },
    };
  }
</script>

<style>
  .popup {
    position: relative;
    z-index: 10000;
    isolation: isolate;
    background: var(--glass-bg-elevated);
    backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
    -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
    color: var(--main-fg, var(--fg));
    padding: 0;
    border-radius: var(--border-radius);
    margin: 5px;
    border: 1px solid var(--glass-border);
    box-shadow:
      var(--glass-highlight),
      0 0 0 1px rgba(var(--shadow-color), 0.06),
      0 12px 28px rgba(var(--shadow-color), 0.22);
    pointer-events: auto;
    overflow: hidden;
  }
</style>
