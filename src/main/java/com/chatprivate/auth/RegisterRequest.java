package com.chatprivate.auth;

public class RegisterRequest {

    private String username;
    private String email;
    private String password;
    private String publicKey;

    // ... constructores existentes ...

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    // --- AÑADIR GETTER Y SETTER PARA LA CLAVE ---
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
}