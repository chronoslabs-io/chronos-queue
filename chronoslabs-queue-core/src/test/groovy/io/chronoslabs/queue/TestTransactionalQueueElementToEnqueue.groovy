package io.chronoslabs.queue

record TestTransactionalQueueElementToEnqueue(TestPayload payload)
    implements TransactionalQueueElementToEnqueue<TestPayload, TestTransactionalQueueElement> {

    TestTransactionalQueueElement.Builder toTransactionalQueueElementBuilder() {
        return TestTransactionalQueueElement.builder().withPayload(payload)
    }

    private static final Map<String, String> DEFAULT_PROPERTIES = [
        "payload.payloadProperty1": "valueOfPayloadProperty1",
        "payload.payloadProperty2": "valueOfPayloadProperty2",
    ] as Map<String, String>

    static TestTransactionalQueueElementToEnqueue aTestTransactionalQueueElementToEnqueue(Map<String, String> customProperties = [:]) {
        Map<String, String> props = DEFAULT_PROPERTIES + customProperties
        TestPayload payload = new TestPayload(
            props.get("payload.payloadProperty1"),
            props.get("payload.payloadProperty2")
        )
        return new TestTransactionalQueueElementToEnqueue(payload)
    }
}
