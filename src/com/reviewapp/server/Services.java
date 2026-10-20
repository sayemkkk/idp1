package com.reviewapp.server;

import com.reviewapp.config.AppConfig;
import com.reviewapp.dao.SessionDao;
import com.reviewapp.dao.UserDao;
import com.reviewapp.model.Role;
import com.reviewapp.model.Session;
import com.reviewapp.model.User;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Application services shared by all handlers: session lifecycle and the
 * resolved identity of the current request. All SQL errors are surfaced as
 * unchecked {@link RuntimeException}s that the dispatcher maps to HTTP 500.
 */
public final class Services {

    private final SessionDao sessionDao;
    private final UserDao userDao;

    public Services(SessionDao sessionDao, UserDao userDao) {
        this.sessionDao = sessionDao;
        this.userDao = userDao;
    }

    public UserDao users() {
        return userDao;
    }

    public SessionDao sessions() {
        return sessionDao;
    }

    /** Creates and persists a new session for the given user. */
    public Session createSession(User user) {
        Instant expires = Instant.now().plusSeconds(AppConfig.SESSION_TTL_SECONDS);
        Session session = new Session(
                com.reviewapp.util.TokenGenerator.sessionId(),
                user.id(),
                user.role(),
                expires);
        try {
            sessionDao.create(session);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create session", e);
        }
        return session;
    }

    /** Resolves and validates the current session, returning the user if valid. */
    public Optional<User> resolveUser(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<Session> session = sessionDao.find(sessionId);
            if (session.isEmpty()) {
                return Optional.empty();
            }
            Session s = session.get();
            if (s.isExpired()) {
                sessionDao.delete(s.id());
                return Optional.empty();
            }
            Optional<User> user = userDao.findById(s.userId());
            if (user.isEmpty() || !user.get().active()) {
                return Optional.empty();
            }
            return user;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resolve session", e);
        }
    }

    public void logout(String sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            sessionDao.delete(sessionId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete session", e);
        }
    }

    public void requireRole(User user, Role... allowed) {
        for (Role role : allowed) {
            if (user.role() == role) {
                return;
            }
        }
        throw ApiException.forbidden("Insufficient privileges for this action");
    }
}
