"""Generate Java 8 transport-only POJOs from the reviewed A/B v2 field list."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3] / "group-buy-market-contract" / "src" / "main" / "java" / "cn" / "bugstack" / "api" / "v2"

# A tuple is (class name, base type, fields). No framework or persistence types cross this boundary.
SCHEMA = [
    ("BaseRequest", None, "String source; String channel"),
    ("TrialRequest", "BaseRequest", "String userId; String goodsId; Integer quantity; String productVersion; String activityId"),
    ("TrialResponse", None, "Boolean available; Boolean visible; Boolean eligible; String reasonCode; Offer offer; String quotedAt"),
    ("Offer", None, "String activityId; String goodsId; String originalPrice; String deductionPrice; String payPrice; String currency; Integer targetCount; String productVersion"),
    ("ReserveRequest", "BaseRequest", "String userId; String outTradeNo; String goodsId; String activityId; Integer quantity; String productVersion; String expectedPayPrice; String currency; String teamId"),
    ("TradeQueryRequest", "BaseRequest", "String userId; String outTradeNo"),
    ("TeamQueryRequest", "BaseRequest", "String teamId; String userId"),
    ("CloseRequest", "BaseRequest", "String userId; String outTradeNo; String closeId; String reason; String paymentDisposition; String verifiedAt"),
    ("CloseResponse", None, "String outTradeNo; String orderId; String teamId; String orderStatus; String closedAt; String recordVersion; String teamVersion"),
    ("ReconciliationQueryRequest", "BaseRequest", "String teamId"),
    ("ReconciliationCompleteRequest", "BaseRequest", "String teamId; String reconciliationId; String rosterVersion; String completionId; String checkedAt; java.util.List<ReconciliationDecision> results"),
    ("ReconciliationDecision", None, "String userId; String outTradeNo; String disposition; String paymentId; String closeId; String paidAt; String paidAmount; String currency; String refundId"),
    ("ReconciliationResult", None, "String teamId; String reconciliationId; String teamStatus; String teamVersion; String resolvedAt"),
    ("ReconciliationSnapshot", None, "String teamStatus; String teamVersion; String reconciliationId; String rosterVersion; String validEndTime; java.util.List<ReconciliationMember> members"),
    ("ReconciliationMember", None, "String userId; String outTradeNo; String orderId; String orderStatus; String reservedAt; String paymentDeadline; String payPrice; String currency; Payment payment"),
    ("EventResultQueryRequest", "BaseRequest", "String eventId"),
    ("EventProcessingResult", None, "String eventId; String eventType; String processingStatus; String outcome; String code; String info; String processedAt; Object result"),
    ("Payment", None, "String paymentId; String paidAmount; String currency; String paidAt"),
    ("Refund", None, "String refundId; String refundStatus; String refundVersion; String refundAmount; String currency"),
    ("Reservation", None, "String recordKind; String orderId; String outTradeNo; String userId; String source; String channel; String activityId; String goodsId; String productVersion; Integer quantity; String teamId; String originalPrice; String deductionPrice; String payPrice; String currency; String reservedAt; String paymentDeadline; String orderStatus; String teamStatus; String recordVersion; String teamVersion; String serverTime; String closedAt; Payment payment; Refund refund"),
    ("Team", None, "String teamId; String activityId; String goodsId; String source; String channel; Integer targetCount; Integer reservedCount; Integer paidCount; String teamStatus; String teamVersion; String validStartTime; String validEndTime; Boolean joinable; String serverTime; Reservation ownReservation"),
    ("EventEnvelope", None, "String eventId; String eventType; Integer schemaVersion; String producer; String occurredAt; String source; String channel; String correlationId; Object payload"),
    ("ProductSnapshotChanged", None, "String goodsId; String productVersion; String goodsName; String originalPrice; String currency; String saleStatus"),
    ("PaymentSucceeded", None, "String userId; String outTradeNo; String paymentId; String paidAmount; String currency; String paidAt"),
    ("RefundStatusChanged", None, "String userId; String outTradeNo; String paymentId; String refundId; String refundAmount; String currency; String refundStatus; String refundVersion; String occurredAt; String failureCode; String paidAt; String paidAmount"),
    ("TeamCompleted", None, "String teamId; String activityId; String teamVersion; java.util.List<String> outTradeNoList"),
    ("TeamReconciliationRequired", None, "String teamId; String activityId; String teamVersion; java.util.List<String> outTradeNoList; String reconciliationId; String rosterVersion; String validEndTime"),
    ("TeamFailed", None, "String teamId; String activityId; String teamVersion; java.util.List<String> outTradeNoList; String reconciliationId; String reason; java.util.List<String> refundRequiredOutTradeNoList"),
    ("EventProcessingResultPayload", None, "String originalEventId; String originalEventType; String outcome; String code; String info; String processedAt; Object result"),
]

def generate(name, base, specification):
    fields = []
    for part in specification.split("; "):
        typ, field = part.rsplit(" ", 1)
        fields.append((typ, field))
    source = ["package cn.bugstack.api.v2;", "", "public class " + name + (" extends " + base if base else " implements java.io.Serializable") + " {", "    private static final long serialVersionUID = 1L;"]
    for typ, field in fields:
        source.append("    private " + typ + " " + field + ";")
    source.append("    public " + name + "() {}")
    for typ, field in fields:
        cap = field[0].upper() + field[1:]
        source += ["    public " + typ + " get" + cap + "() { return " + field + "; }", "    public void set" + cap + "(" + typ + " value) { this." + field + " = value; }"]
    source.append("}")
    path = ROOT / (name + ".java")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(source) + "\n", encoding="utf-8")

if __name__ == "__main__":
    for definition in SCHEMA:
        generate(*definition)
