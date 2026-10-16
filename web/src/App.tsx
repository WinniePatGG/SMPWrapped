import { Routes, Route, Navigate } from 'react-router-dom'
import LandingPage from './pages/LandingPage'
import WrappedPage from './pages/WrappedPage'

export default function App() {
  return (
    <Routes>
      <Route index element={<LandingPage />} />
      <Route path="w/:playerName" element={<WrappedPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
