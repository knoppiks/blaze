<script lang="ts">
	import type { FhirObject } from '../resource/resource-card';
	import { joinStrings } from '../util';
	import Single from './address/single.svelte';
	import GrayBadge from './util/gray-badge.svelte';

	export let values: FhirObject[];
</script>

{#if values.length > 1}
	<div class="ring-1 ring-gray-300 rounded-lg">
		<table class="table-fixed w-full">
			<tbody class="divide-y divide-gray-200">
				{#each values as value}
					<tr>
						<td class="px-5 py-3 text-sm text-gray-500 table-cell w-1/3"
							>{joinStrings('/', value.object.use, value.object.type) ?? '<not-available>'}</td
						>
						<td class="px-5 py-3 text-sm text-gray-500 table-cell">
							<Single value={value.object} />
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
{:else if values.length == 1}
	<Single value={values[0].object} />
	{#if values[0].object.use}
		<GrayBadge value={values[0].object.use} />
	{/if}
{/if}
