#!/bin/sh
set -e

ENDPOINT="http://dynamodb-local:8000"
REGION="us-east-1"

aws_local() {
  aws --endpoint-url "$ENDPOINT" --region "$REGION" "$@"
}

until aws_local dynamodb list-tables > /dev/null 2>&1; do
  echo "Waiting for DynamoDB Local..."
  sleep 2
done

aws_local dynamodb create-table \
  --table-name tickets \
  --attribute-definitions \
      AttributeName=pk,AttributeType=S \
      AttributeName=sk,AttributeType=S \
      AttributeName=status,AttributeType=S \
      AttributeName=ticketId,AttributeType=S \
  --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE \
  --global-secondary-indexes '[{
      "IndexName": "idx_status",
      "KeySchema": [
        {"AttributeName":"status","KeyType":"HASH"},
        {"AttributeName":"ticketId","KeyType":"RANGE"}
      ],
      "Projection": {"ProjectionType":"ALL"}
  }]' \
  --billing-mode PAY_PER_REQUEST \
  || echo "tickets table already exists"

aws_local dynamodb update-time-to-live \
  --table-name tickets \
  --time-to-live-specification "Enabled=true,AttributeName=reservationExpiresAt" \
  || echo "TTL already configured on tickets table"

aws_local dynamodb create-table \
  --table-name orders \
  --attribute-definitions \
      AttributeName=pk,AttributeType=S \
      AttributeName=sk,AttributeType=S \
  --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  || echo "orders table already exists"

# Sample data for manual testing: one event with 3 AVAILABLE tickets.
aws_local dynamodb put-item --table-name tickets --item '{
  "pk": {"S": "sample-event-1"},
  "sk": {"S": "METADATA"},
  "eventId": {"S": "sample-event-1"},
  "name": {"S": "Sample Concert"},
  "date": {"S": "2026-09-01T20:00:00Z"},
  "venue": {"S": "Sample Arena"},
  "totalCapacity": {"N": "3"}
}' || true

for ticketId in t1 t2 t3; do
  aws_local dynamodb put-item --table-name tickets --item "{
    \"pk\": {\"S\": \"sample-event-1\"},
    \"sk\": {\"S\": \"${ticketId}\"},
    \"ticketId\": {\"S\": \"${ticketId}\"},
    \"status\": {\"S\": \"AVAILABLE\"},
    \"version\": {\"N\": \"0\"}
  }" || true
done

echo "DynamoDB Local tables ready"
