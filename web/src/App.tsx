import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import HomePage from './pages/HomePage'
import WrappedPage from './pages/WrappedPage'
import LeaderboardPage from './pages/LeaderboardPage'
import StatsPage from './pages/StatsPage'
import PlayerSearchPage from './pages/PlayerSearchPage'
import NotFoundPage from './pages/NotFoundPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="w/:playerName" element={<WrappedPage />} />
        <Route path="leaderboard" element={<LeaderboardPage />} />
        <Route path="stats" element={<StatsPage />} />
        <Route path="search" element={<PlayerSearchPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
