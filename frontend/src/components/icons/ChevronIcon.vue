<script lang="ts" setup>
defineProps<{ size?: number }>()
</script>
<template>
  <svg :width="size||12" :height="size||12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
    <polyline points="9 18 15 12 9 6"/>
  </svg>
</template>
