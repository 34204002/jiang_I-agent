<script lang="ts" setup>
defineProps<{ size?: number }>()
</script>
<template>
  <svg :height="size||12" :width="size||12" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
    <polyline points="9 18 15 12 9 6"/>
  </svg>
</template>
