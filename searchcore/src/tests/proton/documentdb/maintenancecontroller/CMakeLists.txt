# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_maintenancecontroller_test_app TEST
    SOURCES
    maintenancecontroller_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_feedoperation
    searchcore_matching
    searchcore_attribute
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_pcommon
    searchcore_persistenceengine
    searchcore_grouping
    searchcore_proton_metrics
    searchcore_fconfig
    searchlib_test
)
vespa_add_test(NAME searchcore_maintenancecontroller_test_app COMMAND searchcore_maintenancecontroller_test_app)
vespa_add_executable(searchcore_frozenbucketsmap_test_app TEST
    SOURCES
    frozenbucketsmap_test.cpp
    DEPENDS
    searchcore_server
    searchcore_feedoperation
    searchcore_matching
    searchcore_attribute
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_pcommon
    searchcore_persistenceengine
    searchcore_grouping
    searchcore_proton_metrics
    searchcore_fconfig
)
vespa_add_test(NAME searchcore_frozenbucketsmap_test_app COMMAND searchcore_frozenbucketsmap_test_app)
