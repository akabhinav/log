#!/bin/bash
# Generate a large log file for performance testing

OUTPUT_FILE="test-data/large-test.log"
NUM_LINES=1000000  # 1 million lines (~150MB)

echo "Generating $NUM_LINES log entries to $OUTPUT_FILE..."

for i in $(seq 1 $NUM_LINES); do
    TIMESTAMP=$(date -Iseconds -d "2024-01-15 10:00:00 + $i seconds" 2>/dev/null || date -Iseconds)
    LEVEL=$((RANDOM % 5))
    
    case $LEVEL in
        0) LEVEL_STR="DEBUG" ;;
        1) LEVEL_STR="INFO" ;;
        2) LEVEL_STR="WARN" ;;
        3) LEVEL_STR="ERROR" ;;
        4) LEVEL_STR="FATAL" ;;
    esac
    
    THREAD="thread-$((RANDOM % 20))"
    MESSAGE="Processing request $i with data payload size $((RANDOM % 10000)) bytes"
    
    echo "$TIMESTAMP [$THREAD] $LEVEL_STR  com.example.service.TestService - $MESSAGE"
done > $OUTPUT_FILE

echo "Generated $NUM_LINES lines in $OUTPUT_FILE"
ls -lh $OUTPUT_FILE
