-- Seed the default TUTOR assignment for the provider-neutral deployment model introduced in V26.
-- The fake provider declares no credential fields, so these empty bytea columns do not contain
-- provider secrets and are ignored by the runtime gateway.
INSERT INTO llm.provider_credentials (
  id,
  provider_key,
  display_name,
  public_configuration,
  encrypted_secrets,
  secret_nonce,
  secret_mask,
  created_by_user_id
) VALUES (
  '00000000-0000-0000-0000-000000172622',
  'fake',
  'Fake provider',
  '{}'::jsonb,
  decode('', 'hex'),
  decode('', 'hex'),
  'sin secretos',
  '00000000-0000-0000-0000-000000000622'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO llm.model_deployments (
  id,
  credential_id,
  provider_key,
  adapter_version,
  model_id,
  model_version,
  state,
  capabilities,
  evaluator_state
) VALUES (
  '00000000-0000-0000-0000-000000620622',
  '00000000-0000-0000-0000-000000172622',
  'fake',
  '1',
  'fake-socratic-v1',
  '1',
  'ENABLED',
  '{}'::jsonb,
  'CANDIDATE'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO llm.function_model_config (
  function,
  model_deployment_id,
  enabled
) VALUES (
  'tutor',
  '00000000-0000-0000-0000-000000620622',
  true
) ON CONFLICT (function) DO NOTHING;
