import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Forward all /api/* requests to Spring Boot
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        // SSE requires no buffering — disable response buffering
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            // Keep SSE connections alive through the proxy
            proxyRes.headers['x-accel-buffering'] = 'no';
          });
        },
      },
    },
  },
});
