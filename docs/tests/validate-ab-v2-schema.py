"""Validate the wire schema, public examples and optionally saved real Outbox bodies.

Install jsonschema==4.25.1 and rfc3339-validator==0.1.4 in the ignored local directory.
No credentials or message bodies are printed. --database uses read-only isolated DB queries.
"""
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'docs/dev-ops/ab-v2/.local/schema-validator'))
from jsonschema import Draft202012Validator, FormatChecker

schema = json.loads((ROOT / 'docs/schemas/ab-v2-event-v1.schema.json').read_text(encoding='utf-8'))
subprocess.run([sys.executable, str(ROOT / 'docs/dev-ops/ab-v2/generate-event-schema.py'), '--check'], check=True)
Draft202012Validator.check_schema(schema)
checker = FormatChecker()
assert not checker.conforms('2026-02-30T00:00:00.000Z', 'date-time'), 'Install rfc3339-validator to enable calendar validation'
validator = Draft202012Validator(schema, format_checker=checker)
examples = json.loads((ROOT / 'docs/schemas/ab-v2-event-v1.examples.json').read_text(encoding='utf-8'))
for example in examples:
    validator.validate(example)
assert len({e['eventType'] for e in examples}) == 7
negative = 0
for example in examples:
    for key, value in [('schemaVersion', '1'), ('eventId', 123), ('eventType', 'UNKNOWN'),
                       ('occurredAt', '2026-02-30T00:00:00.000Z'), ('unexpected', True)]:
        invalid = copy.deepcopy(example)
        invalid[key] = value
        assert list(validator.iter_errors(invalid)), (example['eventType'], key)
        negative += 1
    invalid = copy.deepcopy(example)
    invalid['payload']['unexpected'] = True
    assert list(validator.iter_errors(invalid))
    negative += 1
    for key in example['payload']:
        replacement = None
        if key in ('originalPrice', 'paidAmount', 'refundAmount') and example['payload'][key] is not None:
            replacement = '0.00'
        elif key == 'activityId':
            replacement = 'not-a-number'
        elif key.endswith('List') and example['payload'][key]:
            replacement = [example['payload'][key][0]] * 2
        if replacement is not None:
            invalid = copy.deepcopy(example)
            invalid['payload'][key] = replacement
            assert list(validator.iter_errors(invalid)), key
            negative += 1
for example in examples:
    if example['eventType'] == 'REFUND_STATUS_CHANGED':
        optional = ('failureCode', 'paidAt', 'paidAmount')
        for value in (None, 'omit'):
            variant = copy.deepcopy(example)
            for key in optional:
                if value == 'omit':
                    variant['payload'].pop(key, None)
                else:
                    variant['payload'][key] = None
            validator.validate(variant)

result_examples = json.loads((ROOT / 'docs/schemas/ab-v2-processing-result.examples.json').read_text(encoding='utf-8'))
assert {(e['payload']['originalEventType'], e['payload']['outcome']) for e in result_examples} == {
    (kind, outcome) for kind in ('PRODUCT_SNAPSHOT_CHANGED', 'PAYMENT_SUCCEEDED', 'REFUND_STATUS_CHANGED')
    for outcome in ('APPLIED', 'IGNORED', 'REJECTED')}
for example in result_examples:
    validator.validate(example)
    result = example['payload']['result']
    if result is not None:
        # Every promised result field must be required in both success outcomes.
        for key in result:
            invalid = copy.deepcopy(example)
            del invalid['payload']['result'][key]
            assert list(validator.iter_errors(invalid)), key
            negative += 1
            invalid = copy.deepcopy(example)
            invalid['payload']['result'][key] = None
            assert list(validator.iter_errors(invalid)), key
            negative += 1
            if key.endswith('Version'):
                boundary = copy.deepcopy(example)
                boundary['payload']['result'][key] = '9223372036854775807'
                validator.validate(boundary)
                for value in ('9223372036854775808', '1\n'):
                    invalid = copy.deepcopy(example)
                    invalid['payload']['result'][key] = value
                    assert list(validator.iter_errors(invalid)), (key, value)
                    negative += 1
        if 'payment' in result:
            for key in result['payment']:
                invalid = copy.deepcopy(example)
                del invalid['payload']['result']['payment'][key]
                assert list(validator.iter_errors(invalid)), key
                negative += 1
                invalid = copy.deepcopy(example)
                invalid['payload']['result']['payment'][key] = None
                assert list(validator.iter_errors(invalid)), key
                negative += 1
            for amount in ('0.01', '999999.99'):
                boundary = copy.deepcopy(example)
                boundary['payload']['result']['payment']['paidAmount'] = amount
                validator.validate(boundary)
            for key, value in (('paidAmount', '80.00\n'), ('paidAmount', '1000000.00'),
                               ('paymentId', 'payment-1\n'), ('paidAt', '2026-10-02T23:59:59.000Z\n')):
                invalid = copy.deepcopy(example)
                invalid['payload']['result']['payment'][key] = value
                assert list(validator.iter_errors(invalid)), (key, value)
                negative += 1
invalid_examples = json.loads((ROOT / 'docs/schemas/ab-v2-processing-result.invalid-examples.json').read_text(encoding='utf-8'))
for example in invalid_examples:
    assert list(validator.iter_errors(example['event'])), example['name']
    negative += 1

# Validate the literal examples maintained in the contract too.
contract_examples = 0
for line in (ROOT / 'docs/ab-api-contract.md').read_text(encoding='utf-8-sig').splitlines():
    if line.startswith('{"eventId"'):
        validator.validate(json.loads(line))
        contract_examples += 1

real = 0
if '--database' in sys.argv:
    password = os.environ.get('PHASE1_DB_PASSWORD') or os.environ.get('SPRING_DATASOURCE_PASSWORD')
    if not password:
      raise RuntimeError('Set PHASE1_DB_PASSWORD or SPRING_DATASOURCE_PASSWORD for database validation')
    env = dict(os.environ, MYSQL_PWD=password)
    query = "select body from ab_outbox"
    output = subprocess.run(['docker', 'exec', '-e', 'MYSQL_PWD', 'mysql', 'mysql', '-uroot',
                             '--batch', '--raw', '--skip-column-names', 'group_buy_market_phase1_test', '-e', query],
                            env=env, capture_output=True, text=True, encoding='utf-8', check=True).stdout
    for line in output.splitlines():
        validator.validate(json.loads(line))
        real += 1
    assert real > 0, 'No persisted event bodies found'
print(f'Schema valid; seven public examples; {len(result_examples)} processing-result examples; {negative} invalid variants rejected; {contract_examples} contract examples; {real} persisted bodies validated.')
