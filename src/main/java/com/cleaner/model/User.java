package com.cleaner.model;

public class User {
    public long id;
    public String username;
    public transient String passwordHash;
    public String role;
}
