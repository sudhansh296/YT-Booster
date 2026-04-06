import axios from 'axios';

const ADMIN_PATH = import.meta.env.VITE_ADMIN_PATH || 'ytadm1n_x9k2p7';
const BASE = `https://api.picrypto.in/${ADMIN_PATH}`;

const headers = () => ({ 'x-admin-token': localStorage.getItem('admin_token') || '' });

export const adminLogin = (secret) => axios.post(`${BASE}/login`, { secret });
export const getStats = () => axios.get(`${BASE}/stats`, { headers: headers() });
export const getUsers = (params = {}) => axios.get(`${BASE}/users`, { headers: headers(), params });
export const adjustCoins = (id, coins, reason) => axios.post(`${BASE}/users/${id}/coins`, { coins, reason }, { headers: headers() });
export const toggleBan = (id) => axios.post(`${BASE}/users/${id}/ban`, {}, { headers: headers() });
export const getPromoted = () => axios.get(`${BASE}/promoted`, { headers: headers() });
export const addPromoted = (data) => axios.post(`${BASE}/promoted`, data, { headers: headers() });
export const togglePromoted = (id) => axios.post(`${BASE}/promoted/${id}/toggle`, {}, { headers: headers() });
export const deletePromoted = (id) => axios.delete(`${BASE}/promoted/${id}`, { headers: headers() });
export const sendNotification = (data) => axios.post(`${BASE}/send-notification`, data, { headers: headers() });

export const ADMIN_BASE = `/${ADMIN_PATH}`;

// Default axios instance
const api = axios.create({ baseURL: 'https://api.picrypto.in' });
api.interceptors.request.use(config => {
  config.headers['x-admin-token'] = localStorage.getItem('admin_token') || '';
  return config;
});
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 403) {
      localStorage.removeItem('admin_token');
      window.location.reload();
    }
    return Promise.reject(err);
  }
);
export default api;
