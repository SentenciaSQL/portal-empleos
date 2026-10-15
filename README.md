# EmpleoRD

Portal de empleo para República Dominicana. Monolito modular: API Spring Boot y cliente Angular. Las ofertas las publican las empresas; el sistema no inventa vacantes ni salarios.

## Qué incluye esta entrega

Fase 1, más lo necesario para que los botones de la interfaz llamen a la API:

- Registro, verificación de correo, inicio de sesión, recuperación de contraseña y JWT.
- Perfil de candidato (experiencia, formación, certificaciones, habilidades, idiomas, expectativas) y carga de currículum PDF.
- Perfil de empresa, logotipo y varios reclutadores.
- Publicar, editar, pausar y cerrar ofertas. Búsqueda pública con filtros combinables.
- Postulación, favoritos, estados de candidatura, notas internas y currículum con enlace firmado.
- Avisos en el portal y plantillas de correo en español e inglés. El envío real queda apagado hasta configurar SMTP.
- Verificación de empresas, moderación, reportes, planes editables y destaque de ofertas.
- Estadísticas calculadas con los datos guardados.
- URLs de ofertas, sitemap, robots.txt y HTML con Open Graph para redes sociales.
- Currículum PDF generado desde el perfil del candidato, separado del PDF que la persona sube.
- Alertas de empleo con filtros combinables, frecuencia inmediata, diaria o semanal, y avisos sin duplicar la misma oferta.
- Interfaz en español (predeterminado) e inglés. El selector está en la navegación y la preferencia se guarda en el navegador y en la cuenta.
- Contratación del plan de pago mediante PayPal Subscriptions. Sin credenciales no hay cobro y ninguna transacción se marca como pagada.

## Pagos

El precio se lee de `subscription_plans`. El valor inicial del plan profesional es un dato sembrado, no una constante del código.

PayPal es el proveedor porque, a la fecha de esta integración, admite suscripciones en DOP y una página de aprobación donde la empresa paga sin que EmpleoRD guarde la tarjeta. Stripe no habilita República Dominicana como país de la cuenta comercial. Las suscripciones de Mercado Pago no están disponibles para comercios dominicanos. Azul y Cardnet sí operan en el país, pero exigen afiliación comercial y, en el caso de Azul, acceso por VPN o TLS mutuo; la interfaz `PaymentGateway` permite sustituir el proveedor sin reescribir la lógica de planes.

Limitaciones:

- Cada contratación crea un plan de facturación en PayPal con el precio vigente. Una suscripción ya creada conserva el precio con el que se aprobó.
- El plan local no cambia cuando el navegador vuelve de PayPal. Solo un webhook con firma verificada puede activarlo, registrar un pago, marcarlo como fallido, cancelarlo o vencerlo.
- El mismo evento de PayPal no se aplica dos veces.
- Si `PAYPAL_ENABLED` no es `true` o faltan credenciales, el checkout responde que la pasarela no está configurada.
- Recibir fondos en República Dominicana puede requerir el socio local con licencia que PayPal indique para esa cuenta. Eso es una configuración de la cuenta PayPal, no de este repositorio.

Variables:

```bash
PAYPAL_ENABLED=false
PAYPAL_MODE=sandbox
PAYPAL_CLIENT_ID=
PAYPAL_CLIENT_SECRET=
PAYPAL_WEBHOOK_ID=
```

Para activarlo: crea una aplicación REST en el sandbox de PayPal, copia el client id y el secret, crea un webhook apuntando a `https://<tu-api>/api/payments/paypal/webhook` para los eventos `BILLING.SUBSCRIPTION.ACTIVATED`, `BILLING.SUBSCRIPTION.CANCELLED`, `BILLING.SUBSCRIPTION.EXPIRED`, `BILLING.SUBSCRIPTION.SUSPENDED`, `BILLING.SUBSCRIPTION.PAYMENT.FAILED` y `PAYMENT.SALE.COMPLETED`, y pon su id en `PAYPAL_WEBHOOK_ID`. En producción usa `PAYPAL_MODE=live` junto con `APP_PRODUCTION=true`.

La empresa dueña contrata desde `/empresa/suscripcion`. Un reclutador puede ver el estado, no contratar. Un administrador puede asignar un plan sin pasar por PayPal; esa asignación no cancela una suscripción que ya exista en PayPal.

## Publicidad

Los anuncios quedan apagados hasta configurar Google AdSense. Sin Publisher ID válido no se carga el script, no se reserva espacio y `ads.txt` no anuncia un editor.

Hacen falta las dos condiciones: `ADSENSE_ENABLED=true` y la opción global del panel en `/admin/publicidad`. El Publisher ID (`ca-pub-` y 16 dígitos) y los identificadores de bloque pueden venir del entorno o del panel. El entorno tiene prioridad cuando trae un valor válido. Esos identificadores son públicos; no hace falta una clave secreta en el frontend.

El script de AdSense se inserta una sola vez, y solo después de que la persona acepte en el aviso o en la página de privacidad. Rechazar no carga el script. No hay anuncios en el acceso, el registro, la postulación, el pago ni los paneles de gestión.

Espacios: `home-top`, `home-feed`, `job-search-inline`, `job-detail-bottom`, `category-content`, `info-content`. `resource-content` queda preparado para una sección editorial que todavía no existe y no se muestra.

Las vistas del panel cuentan cuántas veces el espacio entró en pantalla. No son ingresos, clics ni RPM. Esos informes se consultan en AdSense.

Para asociar el dominio: en AdSense agrega el sitio, publica `https://<tu-dominio>/ads.txt` (el portal lo genera cuando la publicidad está activa) y pide la revisión cuando el sitio cumpla las políticas del programa. Hasta entonces los espacios siguen vacíos.

```bash
ADSENSE_ENABLED=false
ADSENSE_PUBLISHER_ID=
ADSENSE_SLOT_HOME_TOP=
ADSENSE_SLOT_HOME_FEED=
ADSENSE_SLOT_JOB_SEARCH_INLINE=
ADSENSE_SLOT_JOB_DETAIL_BOTTOM=
ADSENSE_SLOT_CATEGORY_CONTENT=
ADSENSE_SLOT_INFO_CONTENT=
ADSENSE_SLOT_RESOURCE_CONTENT=
```

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
- PayPal, si se usa, exige `PAYPAL_ENABLED=true`, `PAYPAL_MODE=live` y las tres credenciales. El webhook público es `POST /api/payments/paypal/webhook`.
- `APP_PRODUCTION=true` rechaza los secretos de desarrollo.
- Los currículums se guardan en `STORAGE_PATH`. En un disco efímero, monta un volumen.
- El correo sale por SMTP (`MAIL_ENABLED=true` y el resto de variables `MAIL_*`).

## Estructura

- `backend/src/main/java/com/empleord`: módulos `auth`, `catalog`, `candidate`, `company`, `job`, `application`, `notification`, `admin`, `billing`, `seo`, `security`.
- `backend/src/main/resources/db/migration`: esquema, catálogos de República Dominicana y cuentas de demostración.
- `frontend/src/app`: `core`, `shared`, `auth`, `jobs`, `candidates`, `companies`, `applications`, `notifications`, `admin`.
