#
# Copyright (C) 2019 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

$(call inherit-product, device/asus/I001D/device.mk)

# Inherit some common Havoc stuff.
$(call inherit-product, vendor/havoc/config/common_full_phone.mk)

# Device identifier. This must come after all inclusions.
PRODUCT_BRAND := asus
PRODUCT_DEVICE := I001D
PRODUCT_MANUFACTURER := asus
PRODUCT_MODEL := ASUS_I001_1
PRODUCT_NAME := havoc_I001D

PRODUCT_GMS_CLIENTID_BASE := android-asus

# Build info
PRODUCT_BUILD_PROP_OVERRIDES += \
    TARGET_DEVICE=WW_I001D \
    PRODUCT_NAME=WW_I001D

# Official
export export HAVOC_BUILD_TYPE=Official

VENDOR_RELEASE := 10/QKQ1.190825.002/17.0230.2004.60-0:user/release-keys
BUILD_FINGERPRINT := asus/WW_I001D/ASUS_I001_1:$(VENDOR_RELEASE)

PLATFORM_SECURITY_PATCH_OVERRIDE := 2020-04-05
