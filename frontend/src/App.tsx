import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import Repositories from './pages/Repositories';
import Search from './pages/Search';
import Analyze from './pages/Analyze';
import Navbar from './components/Navbar';
import { JSX } from 'react/jsx-runtime';

const PrivateRoute = ({ children }: { children: JSX.Element }) => {
  const token = localStorage.getItem('token');
  return token ? children : <Navigate to="/login" />;
};

const Layout = ({ children }: { children: JSX.Element }) => (
  <>
    <Navbar />
    {children}
  </>
);

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/repos" element={
          <PrivateRoute>
            <Layout><Repositories /></Layout>
          </PrivateRoute>
        } />
        <Route path="/search" element={
          <PrivateRoute>
            <Layout><Search /></Layout>
          </PrivateRoute>
        } />
        <Route path="/analyze" element={
          <PrivateRoute>
            <Layout><Analyze /></Layout>
          </PrivateRoute>
        } />
        <Route path="*" element={<Navigate to="/login" />} />
      </Routes>
    </BrowserRouter>
  );
}