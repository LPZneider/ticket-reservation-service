#!/bin/sh
set -e

REGION="us-east-1"

awslocal sqs create-queue --queue-name purchase-requests --region "$REGION"
awslocal sqs create-queue --queue-name reservation-expiry --region "$REGION"

echo "LocalStack queues ready"
