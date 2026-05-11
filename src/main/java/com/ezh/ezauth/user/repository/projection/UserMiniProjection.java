package com.ezh.ezauth.user.repository.projection;

public interface UserMiniProjection {
    Long getId();
    String getUserType();
    String getUserUuid();
    Long getBranchId();
    String getBranchName();
    String getFullName();
    String getEmail();
    String getPhone();
}
