package com.ezh.ezauth.user.repository.projection;

public interface UserMiniProjection {
    Long getId();
    String getUserType();
    String getUserUuid();
    String getFullName();
}
