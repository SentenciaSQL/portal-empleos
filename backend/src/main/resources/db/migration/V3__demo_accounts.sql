-- Cuentas de demostración. No representan personas ni empresas reales.
-- Contraseña de las tres cuentas: Demo-EmpleoRD-2026!

INSERT INTO users (
    id, role_id, email, password_hash, first_name, last_name, phone, status, email_verified, created_at, updated_at
) VALUES
    (
        '00000000-0000-4000-8000-000000000001',
        3,
        'admin@demo.empleord.do',
        '$2b$12$qQVaOqBQ1GNPgmSDOfXDZOGIeW4kFpTpC26.pmnMZcBjGeo5tHGBK',
        'Admin',
        'Demostración',
        NULL,
        'ACTIVE',
        TRUE,
        NOW(),
        NOW()
    ),
    (
        '00000000-0000-4000-8000-000000000002',
        1,
        'candidato@demo.empleord.do',
        '$2b$12$qQVaOqBQ1GNPgmSDOfXDZOGIeW4kFpTpC26.pmnMZcBjGeo5tHGBK',
        'Candidato',
        'Demostración',
        NULL,
        'ACTIVE',
        TRUE,
        NOW(),
        NOW()
    ),
    (
        '00000000-0000-4000-8000-000000000003',
        2,
        'reclutador@demo.empleord.do',
        '$2b$12$qQVaOqBQ1GNPgmSDOfXDZOGIeW4kFpTpC26.pmnMZcBjGeo5tHGBK',
        'Reclutador',
        'Demostración',
        NULL,
        'ACTIVE',
        TRUE,
        NOW(),
        NOW()
    );

INSERT INTO candidate_profiles (user_id, headline, summary, open_to_work, salary_currency, created_at, updated_at)
VALUES (
    '00000000-0000-4000-8000-000000000002',
    'Perfil de demostración',
    'Esta cuenta sirve para recorrer el portal. No es un candidato real.',
    TRUE,
    'DOP',
    NOW(),
    NOW()
);

INSERT INTO employer_profiles (user_id, job_title, created_at, updated_at)
VALUES (
    '00000000-0000-4000-8000-000000000003',
    'Cuenta de demostración',
    NOW(),
    NOW()
);

INSERT INTO companies (
    id, name, slug, description, country_code, verification_status, status, created_at, updated_at
) VALUES (
    '00000000-0000-4000-8000-000000000010',
    'Empresa Demostración EmpleoRD',
    'empresa-demostracion',
    'Cuenta de demostración para publicar ofertas de prueba. No representa un empleador real y no tiene vacantes precargadas.',
    'DO',
    'PENDING',
    'ACTIVE',
    NOW(),
    NOW()
);

INSERT INTO company_members (id, company_id, user_id, member_role, created_at)
VALUES (
    '00000000-0000-4000-8000-000000000011',
    '00000000-0000-4000-8000-000000000010',
    '00000000-0000-4000-8000-000000000003',
    'OWNER',
    NOW()
);

INSERT INTO company_subscriptions (id, company_id, plan_id, status, started_at, created_at, updated_at)
VALUES (
    '00000000-0000-4000-8000-000000000012',
    '00000000-0000-4000-8000-000000000010',
    (SELECT id FROM subscription_plans WHERE code = 'FREE'),
    'ACTIVE',
    NOW(),
    NOW(),
    NOW()
);
