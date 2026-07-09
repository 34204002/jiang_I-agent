<script lang="ts" setup>
defineProps<{ size?: number }>()
</script>
<template>
  <svg :width="size||14" :height="size||14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
    <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
  </svg>
</template>
