package com.catalinionescu.jeromqserver.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Uses code inspired from ApacheUtils.SerializationUtils.
 * 
 * @author Catalin Ionescu
 */
public class SerializationUtils {
    /**
     * Serializes an {@code Object} to a byte array for storage/serialization.
     * 
     * @param obj the object to serialize
     * @return a byte[] with the converted Serializable or null on error
     */
    public static byte[] serialize(final Serializable obj) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
                ObjectOutputStream out = new ObjectOutputStream(baos)) {
            out.writeObject(obj);
            return baos.toByteArray();
        } catch (IOException e) {
            // Can't happen with a ByteArrayOutputStream?
            return null;
        }
    }

    /**
     * Deserializes a single {@code Object} from an array of bytes.
     * 
     * @param <T> the object type to be deserialized
     * @param objectData the serialized object, must not be null
     * @return the deserialized object
     * @throws ClassNotFoundException
     * @throws IllegalArgumentException if {@code objectData} is {@code null}
     */
    public static <T> T deserialize(final byte[] objectData) throws ClassNotFoundException, IllegalArgumentException {
        if (objectData == null) {
            throw new IllegalArgumentException("The byte[] must not be null");
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(objectData))) {
            @SuppressWarnings("unchecked")
            final T obj = (T) in.readObject();
            return obj;
        } catch (IOException e) {
            return null;
        }
    }
}
