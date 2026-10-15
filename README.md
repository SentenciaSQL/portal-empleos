# EmpleoRD

Portal de empleo para República Dominicana. Monolito modular: API Spring Boot y cliente Angular. Las ofertas las publican las empresas; el sistema no inventa vacantes ni salarios.

## Qué incluye esta entrega

Fase 1, más lo necesario para que los botones de la interfaz llamen a la API:

- Registro, verificación de correo, inicio de sesión, recuperación de contraseña y JWT.
- Perfil de candidato (experiencia, formación, certificaciones, habilidades, idiomas, expectativas) y carga de currículum PDF.
- Perfil de empresa, logotipo y varios reclutadores.
- Publicar, editar, pausar y cerrar ofertas. Búsqueda pública con filtros combinables.
- Postulación, favoritos, estados de candidatura, notas internas y currículum con enlace firmado.
- Avisos en el portal y plantillas de correo en español. El envío real queda apagado hasta configurar SMTP.
- Verificación de empresas, moderación, reportes, planes editables y destaque de ofertas.
- Estadísticas calculadas con los datos guardados.
- URLs de ofertas, sitemap, robots.txt y HTML con Open Graph para redes sociales.

Queda preparado y no está terminado:

- Cobro real. Existe la interfaz `PaymentGateway`; no hay checkout.
- Alertas de empleo por correo: la tabla y el contador existen; no hay pantalla para crearlas.
- Currículum PDF generado desde el perfil. En esta fase se sube un PDF.
- Interfaz en inglés y expansión a otros países. Los catálogos de país ya están en la base.

## Cuentas de demostración

Contraseña de las tres: `Demo-EmpleoRD-2026!`

| Correo | Rol |
| --- | --- |
| candidato@demo.empleord.do | Candidato |
| reclutador@demo.empleord.do | Dueño de Empresa Demostración EmpleoRD |
| admin@demo.empleord.do | Administrador |

La empresa de demostración no es un empleador real y no trae ofertas. El plan gratuito permite 2 ofertas activas. El plan profesional se siembra a 3490 DOP al mes y un administrador puede cambiar el precio.

## Ejecutar en local

Requisitos: Java 21, Maven, Node.js 22.12 o superior, PostgreSQL 16.

```bash
sudo service postgresql start
sudo -u postgres psql -c "CREATE USER empleord WITH PASSWORD 'empleord';" || true
sudo -u postgres psql -c "CREATE DATABASE empleord OWNER empleord;" || true
cp .env.example .env
```

API:

```bash
cd backend
DATABASE_PASSWORD=empleord MAIL_LOG_LINKS=true mvn spring-boot:run
```

La API queda en http://localhost:8080. OpenAPI: http://localhost:8080/swagger-ui.html. Con `MAIL_LOG_LINKS=true` los enlaces de verificación y de contraseña se escriben en el log.

Frontend:

```bash
cd frontend
npm ci
npx ng serve
```

La interfaz queda en http://localhost:4200 y el proxy envía `/api` a la API.

## Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

Interfaz en http://localhost:8088. API directa en http://localhost:8080. Postgres solo escucha en 127.0.0.1:5432. La contraseña `empleord` del Compose es un respaldo de desarrollo; defínela en `.env`.

## Railway o Render

- La API lee `PORT`.
- Si el proveedor entrega `DATABASE_URL` como `postgres://usuario:clave@host:5432/db`, el arranque lo convierte a JDBC.
- Define `JWT_SECRET`, `FILE_SIGNING_SECRET`, `COOKIE_SECURE=true`, `APP_PRODUCTION=true`, `APP_PUBLIC_URL` y `API_PUBLIC_URL`.
- `APP_PRODUCTION=true` rechaza los secretos de desarrollo.
- Los currículums se guardan en `STORAGE_PATH`. En un disco efímero, monta un volumen.
- El correo sale por SMTP (`MAIL_ENABLED=true` y el resto de variables `MAIL_*`).

## Estructura

- `backend/src/main/java/com/empleord`: módulos `auth`, `catalog`, `candidate`, `company`, `job`, `application`, `notification`, `admin`, `billing`, `seo`, `security`.
- `backend/src/main/resources/db/migration`: esquema, catálogos de República Dominicana y cuentas de demostración.
- `frontend/src/app`: `core`, `shared`, `auth`, `jobs`, `candidates`, `companies`, `applications`, `notifications`, `admin`.
