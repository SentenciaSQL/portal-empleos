package com.empleord.storage;

import com.empleord.common.ApiException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public final class FileRules {

    private FileRules() {
    }

    public static byte[] pdf(MultipartFile file) {
        byte[] bytes = bytes(file, 5 * 1024 * 1024, "El currículum debe ser un PDF de hasta 5 MB.");
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw ApiException.badRequest("El archivo debe ser un PDF válido.");
        }
        return bytes;
    }

    public static byte[] image(MultipartFile file) {
        byte[] bytes = bytes(file, 2 * 1024 * 1024, "El logotipo debe ser PNG, JPG o WEBP de hasta 2 MB.");
        boolean png = bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean jpg = bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
        boolean webp = bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        if (!png && !jpg && !webp) {
            throw ApiException.badRequest("El logotipo debe ser PNG, JPG o WEBP.");
        }
        return bytes;
    }

    public static String imageType(byte[] bytes) {
        if ((bytes[0] & 0xFF) == 0x89) {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return "image/webp";
    }

    private static byte[] bytes(MultipartFile file, int max, String message) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Selecciona un archivo.");
        }
        if (file.getSize() > max) {
            throw ApiException.badRequest(message);
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("No se pudo leer el archivo.");
        }
    }
}
