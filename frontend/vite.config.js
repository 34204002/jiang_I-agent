import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/css': 'http://localhost:8080',
      '/js': 'http://localhost:8080',
      '/login.html': 'http://localhost:8080',
      '/settings.html': 'http://localhost:8080',
      '/admin.html': 'http://localhost:8080',
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: false, // 保留 common.js、login.html 等静态文件
  }
})
