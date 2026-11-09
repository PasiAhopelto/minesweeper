import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Production build goes straight into Spring Boot's static resources,
// so `mvn package` produces a single runnable jar.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    // During `npm run dev`, forward API calls to the Spring Boot app.
    proxy: { '/api': 'http://localhost:8080' },
  },
})
