package com.ezh.ezauth.user.entity;

public enum AccountScope {
    PLATFORM,  // Kubee internal team — devs, ops, support
    TENANT     // Customer's users — always scoped to a tenant
}
