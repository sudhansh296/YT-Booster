import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const adminPath = env.VITE_ADMIN_PATH || 'ytadm1n_x9k2p7';
  return {
    plugins: [react()],
    base: `/${adminPath}/`,
  };
});
