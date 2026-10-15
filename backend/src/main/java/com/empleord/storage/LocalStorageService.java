package com.empleord.storage;

import com.empleord.common.ApiException;
import com.empleord.config.AppProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(AppProperties properties) throws IOException {
        this.root = Path.of(properties.getStorage().getLocation()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public StoredFile store(String folder, String originalName, byte[] bytes) {
        String safeName = originalName == null ? "archivo" : originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.length() > 80) {
            safeName = safeName.substring(safeName.length() - 80);
        }
        String key = folder + "/" + UUID.randomUUID() + "-" + safeName;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return new StoredFile(key, bytes.length);
        } catch (IOException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el archivo.");
        }
    }

    @Override
    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.exists(path)) {
            throw ApiException.notFound("El archivo no está disponible.");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // El registro de negocio puede continuar si el archivo ya no existe.
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.badRequest("Ruta de archivo no válida.");
        }
        return path;
    }
}
