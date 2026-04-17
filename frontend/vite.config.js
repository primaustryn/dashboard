import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The proxy rewrites /api/* requests to the Spring Boot backend during dev,
// so the browser never sees a cross-origin request (no CORS issues in dev).
// In production, the built frontend is served as static files by Spring Boot
// (or a reverse proxy), making the proxy config irrelevant.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static', // Spring Boot serves from here
    emptyOutDir: true,
  },
})
