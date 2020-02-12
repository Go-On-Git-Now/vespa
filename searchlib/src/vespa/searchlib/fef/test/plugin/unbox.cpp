// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unbox.h"

namespace search::fef::test {

namespace {

struct UnboxExecutor : FeatureExecutor {
    bool isPure() override { return true; }
    void execute(uint32_t docid) override {
        outputs().set_number(0, inputs().get_object(docid, 0).get().as_double());
    }
};

} // namespace search::fef::test::<unnamed>

UnboxBlueprint::UnboxBlueprint()
    : Blueprint("unbox")
{
}

void
UnboxBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

Blueprint::UP
UnboxBlueprint::createInstance() const
{
    return std::make_unique<UnboxBlueprint>();
}

ParameterDescriptions
UnboxBlueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc().feature();
}

bool
UnboxBlueprint::setup(const IIndexEnvironment &, const ParameterList &params)
{
    defineInput(params[0].getValue(), AcceptInput::OBJECT);
    describeOutput("value", "unboxed value", FeatureType::number());
    return true;
}

FeatureExecutor &
UnboxBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    return stash.create<UnboxExecutor>();
}

} // namespace search::fef::test
