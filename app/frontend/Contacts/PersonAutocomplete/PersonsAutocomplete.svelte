<hbox flex class="persons-autocomplete">
  {#each $persons.each as person, index}
    {#if !isCollapsed || collapseAfter == null || index < collapseAfter}
      <PersonEntry {person}
        {onRemovePerson}
        on:focusNext={onFocusNext}
        {disabled}
        >
        <slot name="person-pill-before-avatar" slot="before-avatar" {person} />
        <slot name="person-pill-after-name" slot="after-name" {person} />
        <slot name="person-popup-bottom" slot="person-popup-bottom" {person} />
        <slot name="person-popup-buttons" slot="person-popup-buttons" {person} />
        <slot name="person-context-menu" slot="context-menu" {person} />
      </PersonEntry>
    {/if}
  {/each}
  {#if collapseAfter != null && $persons.length > collapseAfter}
    <hbox class="recipient-collapse buttons">
      {#if isCollapsed}
        <Button
          iconSize="10px"
          label="+ {hiddenPersonCount}"
          tooltip={$t`Show ${hiddenPersonCount} more`}
          classes="font-small"
          onClick={() => isCollapsed = false}
          />
      {:else}
        <Button
          icon={ChevronUpIcon}
          label={$t`Collapse`}
          classes="small collapse font-small"
          iconOnly
          onClick={() => isCollapsed = true}
          />
      {/if}
    </hbox>
  {/if}
  {#if !disabled}
    <hbox flex class="input">
      <PersonAutocomplete
        {onAddPerson}
        skipPersons={$persons}
        searchFunction={searchFunction}
        {placeholder} {tabindex} {autofocus}
        bind:this={autocompleteEl}
        >
        <slot name="result-bottom-row" slot="result-bottom-row" let:person {person} />
      </PersonAutocomplete>
      <slot name="end" />
    </hbox>
  {/if}
</hbox>

<script lang="ts">
  import type { Collection } from "svelte-collections";
  import type { PersonUID } from "../../../logic/Abstract/PersonUID";
  import PersonAutocomplete from "./PersonAutocomplete.svelte";
  import PersonEntry from "./PersonEntry.svelte";
  import Button from "../../Shared/Button.svelte";
  import ChevronUpIcon from "lucide-svelte/icons/chevron-up";
  import { t } from "../../../l10n/l10n";

  /**
   * The persons that the user selected.
   * in/out */
  export let persons: Collection<PersonUID>;
  export let placeholder: string = null;
  export let tabindex = null;
  export let autofocus = false;
  export let disabled = false;
  export let collapseAfter: number | null = null;
  export let searchFunction: (
    (searchText: string, skip: (person: PersonUID) => boolean) => Promise<PersonUID[]>
  ) | null = null;
  export let onAddPerson: (person: PersonUID) => void | Promise<void> = onAddPersonDefault;
  export let onRemovePerson: (person: PersonUID) => void | Promise<void> = onRemovePersonDefault;

  let isCollapsed = true;
  $: hiddenPersonCount = collapseAfter == null ? 0 : Math.max($persons.length - collapseAfter, 0);

  //$: console.log("persons", persons.contents);

  function onAddPersonDefault(person: PersonUID) {
    if (!person || persons.contains(person)) {
      return;
    }
    persons.add(person);
  }
  function onRemovePersonDefault(person: PersonUID) {
    if (!person) {
      return;
    }
    persons.remove(person);
  }
  let autocompleteEl: PersonAutocomplete;
  function onFocusNext() {
    autocompleteEl?.focus();
  }

  export async function commitPendingInput(): Promise<boolean> {
    return autocompleteEl?.commitPendingInput() ?? false;
  }

  export function focusInput() {
    autocompleteEl?.focus();
  }
</script>

<style>
  .persons-autocomplete {
    flex-wrap: wrap;
    border-bottom: 1px solid rgb(0, 0, 0, 7%);
    align-items: center;
    padding: 3px 4px;
  }
  .input {
    margin-inline-start: 4px;
  }
  .persons-autocomplete > :global(*) {
    margin: 3.5px 3px;
  }
  .persons-autocomplete :global(input.autocomplete-input) {
    border: none;
  }
  .recipient-collapse {
    margin-inline-start: 4px;
  }
  .recipient-collapse :global(button) {
    min-height: 22px;
    padding: 0 8px;
    border: none;
    border-radius: 999px;
    font-size: 11px;
  }
  .recipient-collapse :global(button:not(:hover)) {
    background-color: color-mix(in srgb, var(--main-fg) 8%, transparent);
  }
</style>
