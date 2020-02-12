// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "featureoverrider.h"

namespace search {
namespace fef {

void
FeatureOverrider::handle_bind_inputs()
{
    _executor.copy_inputs(inputs());
}

void
FeatureOverrider::handle_bind_outputs()
{
    _executor.copy_outputs(outputs());
}

FeatureOverrider::FeatureOverrider(FeatureExecutor &executor, uint32_t outputIdx, feature_t value)
    : _executor(executor),
      _outputIdx(outputIdx),
      _value(value)
{
}

bool
FeatureOverrider::isPure()
{
    return _executor.isPure();
}

void
FeatureOverrider::execute(uint32_t docId)
{
    forward_execution(docId, _executor);
    if (_outputIdx < outputs().size()) {
        outputs().set_number(_outputIdx, _value);
    }
}

void
FeatureOverrider::handle_bind_match_data(const MatchData &md)
{
    _executor.bind_match_data(md);
}

} // namespace fef
} // namespace search
