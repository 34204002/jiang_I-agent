<script lang="ts" setup>
defineProps<{ size?: number }>()
</script>
<template>
  <svg :height="size||14" :width="size||14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
    <line x1="18" x2="6" y1="6" y2="18"/>
    <line x1="6" x2="18" y1="6" y2="18"/>
  </svg>
</template>
