import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // /api isteklerini Spring Boot backend'ine yönlendir (farklı port için: API_TARGET=http://localhost:8081 npm run dev)
    proxy: { '/api': process.env.API_TARGET || 'http://localhost:8080' },
  },
})
