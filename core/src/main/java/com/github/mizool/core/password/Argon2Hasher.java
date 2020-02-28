package com.github.mizool.core.password;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

public class Argon2Hasher implements PasswordHasher
{
    public static final String ALGORITHM_NAME = "Argon2";

    private static final String ITERATIONS_PROPERTY_NAME = Argon2Hasher.class.getName() + ".iterations";
    private static final String MEMORY_PROPERTY_NAME = Argon2Hasher.class.getName() + ".memory";
    private static final String PARALLELISM_PROPERTY_NAME = Argon2Hasher.class.getName() + ".parallelism";
    private static final int
        ITERATIONS_FOR_NEW_PASSWORDS
        = Integer.parseInt(System.getProperty(ITERATIONS_PROPERTY_NAME, "10"));
    private static final int MEMORY = Integer.parseInt(System.getProperty(MEMORY_PROPERTY_NAME, "65536"));
    private static final int PARALLELISM = Integer.parseInt(System.getProperty(PARALLELISM_PROPERTY_NAME, "1"));

    private final Argon2 argon2 = Argon2Factory.create();

    @Override
    public boolean isResponsibleFor(String algorithm)
    {
        return algorithm.equals(ALGORITHM_NAME);
    }

    @Override
    public String hashPassword(char[] plaintextPassword)
    {
        String result;
        try
        {
            result = argon2.hash(ITERATIONS_FOR_NEW_PASSWORDS, MEMORY, PARALLELISM, plaintextPassword);
        }
        finally
        {
            argon2.wipeArray(plaintextPassword);
        }
        return result;
    }

    @Override
    public boolean passwordsMatch(char[] submittedPlaintext, String digest)
    {
        boolean result;
        try
        {
            result = argon2.verify(digest, submittedPlaintext);
        }
        finally
        {
            argon2.wipeArray(submittedPlaintext);
        }
        return result;
    }
}