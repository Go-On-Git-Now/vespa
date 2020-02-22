// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nns_index_iterator.h"
#include <vespa/searchlib/tensor/nearest_neighbor_index.h>
#include <cmath>

using Neighbor = search::tensor::NearestNeighborIndex::Neighbor;

namespace search::queryeval {

/**
 * Search iterator for K nearest neighbor matching,
 * where the actual search is done up front and this class
 * just iterates over a vector held by the blueprint.
 **/
class NeighborVectorIterator : public NnsIndexIterator
{
private:
    fef::TermFieldMatchData &_tfmd;
    const std::vector<Neighbor> &_hits;
    uint32_t _idx;
    double _last_sq_dist;
public:
    NeighborVectorIterator(fef::TermFieldMatchData &tfmd,
                           const std::vector<Neighbor> &hits)
        : _tfmd(tfmd),
          _hits(hits),
          _idx(0),
          _last_sq_dist(0.0)
    {}

    void initRange(uint32_t begin_id, uint32_t end_id) override {
        SearchIterator::initRange(begin_id, end_id);
        uint32_t lo = 0;
        uint32_t hi = _hits.size();
        while (lo < hi) {
            uint32_t mid = (lo + hi) / 2;
            if (_hits[mid].docid < begin_id) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        _idx = lo;
    }

    void doSeek(uint32_t docId) override {
        while (_idx < _hits.size()) {
            uint32_t hit_id = _hits[_idx].docid;
            if (hit_id < docId) {
                ++_idx;
            } else if (hit_id < getEndId()) {
                setDocId(hit_id);
                _last_sq_dist = _hits[_idx].distance;
                return;
            } else {
                _idx = _hits.size();
            }
        }
        setAtEnd();
    }

    void doUnpack(uint32_t docId) override {
        _tfmd.setRawScore(docId, sqrt(_last_sq_dist));
    }

    Trinary is_strict() const override { return Trinary::True; }
};

std::unique_ptr<NnsIndexIterator>
NnsIndexIterator::create(
        fef::TermFieldMatchData &tfmd,
        const std::vector<Neighbor> &hits)
{
    return std::make_unique<NeighborVectorIterator>(tfmd, hits);
}

} // namespace
