#!/bin/bash

CONFIG_FILE="${LIFERAY_HOME}/osgi/configs/com.liferay.portal.store.s3.configuration.S3StoreConfiguration.config"

if [[ "$LIFERAY_WORKSPACE_ENVIRONMENT" != "local" ]]; then
    if [[ -z "$JOD_OHJAAJA_S3_BUCKET_NAME" || -z "$AWS_REGION" ]]; then
        echo "Warning: JOD_OHJAAJA_S3_BUCKET_NAME or AWS_REGION is missing. Deleting file $CONFIG_FILE."
        rm -f "$CONFIG_FILE"
        exit 1
    fi

    if [[ ! -f "$CONFIG_FILE" ]]; then
        echo "Warning: File $CONFIG_FILE does not exists."
        exit 1
    fi

    sed -i "s|<BUCKET_NAME>|${JOD_OHJAAJA_S3_BUCKET_NAME}|g" "$CONFIG_FILE"
    sed -i "s|<AWS_REGION>|${AWS_REGION}|g" "$CONFIG_FILE"
    echo "Set S3 bucket name $JOD_OHJAAJA_S3_BUCKET_NAME and S3 Region $AWS_REGION"
else
    echo "Skip setting S3 bucket name and S3 Region"
fi



