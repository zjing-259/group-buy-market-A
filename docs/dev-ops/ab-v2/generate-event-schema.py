"""Generate the version-1 wire schema from the saved Java contract DTO fields (no credentials)."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DTO = ROOT / "group-buy-market-contract/src/main/java/cn/bugstack/api/v2"
KINDS = {
    "PRODUCT_SNAPSHOT_CHANGED": "ProductSnapshotChanged",
    "PAYMENT_SUCCEEDED": "PaymentSucceeded",
    "REFUND_STATUS_CHANGED": "RefundStatusChanged",
    "TEAM_COMPLETED": "TeamCompleted",
    "TEAM_RECONCILIATION_REQUIRED": "TeamReconciliationRequired",
    "TEAM_FAILED": "TeamFailed",
    "EVENT_PROCESSING_RESULT": "EventProcessingResultPayload",
}
OPTIONAL = {"RefundStatusChanged": {"failureCode", "paidAt", "paidAmount"},
            "EventProcessingResultPayload": set()}

# JSON Schema patterns are searches, and '$' alone permits a final newline.
END = r"$(?![\s\S])"

def positive_long_pattern():
    maximum = "9223372036854775807"  # V2Format.positiveVersion uses Long.parseLong.
    alternatives = [r"[1-9][0-9]{0,17}"]
    for index, digit in enumerate(maximum):
        lower, upper = (1 if index == 0 else 0), int(digit) - 1
        if lower <= upper:
            smaller = str(lower) if lower == upper else f"[{lower}-{upper}]"
            alternatives.append(maximum[:index] + smaller + f"[0-9]{{{len(maximum)-index-1}}}")
    return "^(?:" + "|".join(alternatives + [maximum]) + ")" + END

def field_schema(name, kind):
    if "List" in kind:
        return {"type": "array", "maxItems": 100, "uniqueItems": True, "items": {"$ref": "#/$defs/id"}}
    if kind == "Object":
        return {"type": ["object", "null"]}
    if name.endswith("At") or name in {"validEndTime"}:
        return {"$ref": "#/$defs/time"}
    if name in {"originalPrice", "paidAmount", "refundAmount"}:
        return {"$ref": "#/$defs/positiveMoney"}
    if name == "activityId" or (name.endswith("Version") and name != "rosterVersion"):
        return {"$ref": "#/$defs/positiveLong"}
    if name == "currency":
        return {"const": "CNY"}
    if name == "goodsName":
        return {"type": "string", "minLength": 1, "maxLength": 128}
    choices = {"saleStatus": ["ON_SALE", "OFF_SALE"], "refundStatus": ["PENDING", "FAILED", "SUCCEEDED"],
               "outcome": ["APPLIED", "IGNORED", "REJECTED"]}
    if name in choices:
        return {"enum": choices[name]}
    if name in {"code", "info", "failureCode", "reason", "originalEventType"}:
        return {"type": "string"}
    return {"$ref": "#/$defs/id"}

definitions = {
    "id": {"type": "string", "minLength": 1, "maxLength": 64, "pattern": r"^\S(?:.*\S)?" + END},
    "scope": {"type": "string", "minLength": 1, "maxLength": 8, "pattern": r"^\S(?:.*\S)?" + END},
    "time": {"type": "string", "format": "date-time", "pattern": r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z" + END},
    "money": {"type": "string", "pattern": r"^(0|[1-9][0-9]{0,5})\.[0-9]{2}" + END},
    "positiveMoney": {"allOf": [{"$ref": "#/$defs/money"}, {"not": {"const": "0.00"}}]},
    "positiveLong": {"type": "string", "maxLength": 19, "pattern": positive_long_pattern()},
}
for class_name in KINDS.values():
    fields = re.findall(r"private (String|Object|java\.util\.List<String>) (\w+);", (DTO / (class_name + ".java")).read_text(encoding="utf-8"))
    optional = OPTIONAL.get(class_name, set())
    properties = {}
    for kind, name in fields:
        value = field_schema(name, kind)
        if name in optional:
            value = {"anyOf": [value, {"type": "null"}]}
        properties[name] = value
    definitions[class_name] = {"type": "object", "additionalProperties": False,
        "properties": properties, "required": [name for _, name in fields if name not in optional]}

# Processing results are immutable facts, not free-form maps. Match V2PaymentEngine.paymentResult
# and V2EventEngine.product/refund, including nested Payment and success/rejection semantics.
def closed_object(properties):
    return {"type": "object", "additionalProperties": False,
            "properties": properties, "required": list(properties)}

def ref(name):
    return {"$ref": "#/$defs/" + name}

version = ref("positiveLong")
definitions["AcceptedPayment"] = closed_object({
    "paymentId": ref("id"), "paidAmount": ref("positiveMoney"),
    "currency": {"const": "CNY"}, "paidAt": ref("time")})
definitions["PaymentProcessingResult"] = closed_object({
    "orderId": ref("id"), "outTradeNo": ref("id"), "teamId": ref("id"),
    "payment": ref("AcceptedPayment"), "orderStatus": {"const": "PAID"},
    "teamStatus": {"enum": ["FORMING", "RECONCILING", "COMPLETED", "FAILED"]},
    "recordVersion": version, "teamVersion": version})
definitions["ProductProcessingResult"] = closed_object({
    "goodsId": ref("id"), "currentVersion": version, "applied": {"type": "boolean"},
    "saleStatus": {"enum": ["ON_SALE", "OFF_SALE"]}})
definitions["RefundProcessingResult"] = closed_object({
    "outTradeNo": ref("id"), "refundId": ref("id"),
    "refundStatus": {"enum": ["PENDING", "FAILED", "SUCCEEDED"]},
    "refundVersion": version, "recordVersion": version})
result_types = {"PRODUCT_SNAPSHOT_CHANGED": "ProductProcessingResult",
                "PAYMENT_SUCCEEDED": "PaymentProcessingResult",
                "REFUND_STATUS_CHANGED": "RefundProcessingResult"}
reply = definitions["EventProcessingResultPayload"]
reply["properties"]["originalEventType"] = {"enum": list(result_types)}
reply["properties"]["code"] = {"type": "string", "minLength": 1}
reply["allOf"] = [{
    "if": {"properties": {"outcome": {"const": "REJECTED"}}},
    "then": {"properties": {"result": {"type": "null"}, "code": {"not": {"const": "0000"}}}},
    "else": {"properties": {"code": {"const": "0000"}}},
}]
for kind, definition in result_types.items():
    reply["allOf"].append({
        "if": {"properties": {"originalEventType": {"const": kind},
                              "outcome": {"enum": ["APPLIED", "IGNORED"]}}},
        "then": {"properties": {"result": ref(definition)}}})
for outcome, applied in [("APPLIED", True), ("IGNORED", False)]:
    reply["allOf"].append({
        "if": {"properties": {"originalEventType": {"const": "PRODUCT_SNAPSHOT_CHANGED"},
                              "outcome": {"const": outcome}}},
        "then": {"properties": {"result": {"properties": {"applied": {"const": applied}}}}}})

schema = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "A/B event envelope, schemaVersion 1",
    "type": "object", "additionalProperties": False,
    "required": ["eventId", "eventType", "schemaVersion", "producer", "occurredAt", "source", "channel", "payload"],
    "properties": {"eventId": {"$ref": "#/$defs/id"}, "eventType": {"enum": list(KINDS)},
        "schemaVersion": {"const": 1, "type": "integer"}, "producer": {"$ref": "#/$defs/id"},
        "occurredAt": {"$ref": "#/$defs/time"}, "source": {"$ref": "#/$defs/scope"}, "channel": {"$ref": "#/$defs/scope"},
        "correlationId": {"anyOf": [{"$ref": "#/$defs/id"}, {"type": "null"}]}, "payload": {"type": "object"}},
    "allOf": [{"if": {"properties": {"eventType": {"const": event_type}}},
        "then": {"properties": {"payload": {"$ref": "#/$defs/" + class_name}}}} for event_type, class_name in KINDS.items()],
    "$defs": definitions,
}
target = ROOT / "docs/schemas/ab-v2-event-v1.schema.json"
target.parent.mkdir(parents=True, exist_ok=True)
rendered = json.dumps(schema, ensure_ascii=False, indent=2) + "\n"
if "--check" in sys.argv:
    if target.read_text(encoding="utf-8") != rendered:
        raise SystemExit("Saved event schema differs from its generator; regenerate it before validation.")
    print("Saved event schema matches its generator")
else:
    target.write_text(rendered, encoding="utf-8")
    print("Generated docs/schemas/ab-v2-event-v1.schema.json")
