#ifndef TERM_SEQ_HPP
#define TERM_SEQ_HPP

#include <memory>
#include <sstream>
#include <vector>

#include "merge_iterator.hpp"
#include "shard.hpp"

namespace imhotep {

    template <typename term_t>
    class TermSeq {
    public:
        typedef MergeIterator<term_t> merge_it;

        TermSeq() { }

        TermSeq(const TermSeq& rhs) = default;

        /* ids must match all items in iterator range! */
        TermSeq(MergeIterator<term_t>& begin, MergeIterator<term_t>& end);

        void reset(MergeIterator<term_t>& begin, MergeIterator<term_t>& end);

        bool operator==(const TermSeq& rhs) const;

        const typename term_t::id_t& id() const { return _id; }

        size_t size() const;

        /*
        const std::vector<const char*>& docid_addresses() const { return _guts._docid_addresses; }
        const std::vector<int32_t>&           doc_freqs() const { return _guts._doc_freqs;       }

        const std::vector<Shard::packed_table_ptr>& tables() const { return _guts._tables; }
        */

        std::vector<const char*>& docid_addresses() const { return _guts._docid_addresses; }
        std::vector<int32_t>&           doc_freqs() const { return _guts._doc_freqs;       }

        std::vector<Shard::packed_table_ptr>& tables() const { return _guts._tables; }

        std::string to_string() const;

    private:
        typename term_t::id_t _id = IdTraits<typename term_t::id_t>::default_value();

        struct Guts {
            std::vector<const char*>             _docid_addresses;
            std::vector<int32_t>                 _doc_freqs;
            std::vector<Shard::packed_table_ptr> _tables;

            void clear() {
                _docid_addresses.clear();
                _doc_freqs.clear();
                _tables.clear();
            }
        };

        mutable Guts _guts;
    };


    template <typename term_t>
    TermSeq<term_t>::TermSeq(MergeIterator<term_t>& begin, MergeIterator<term_t>& end) {
        reset(begin, end);
    }

    template <typename term_t>
    void TermSeq<term_t>::reset(MergeIterator<term_t>& begin,
                                MergeIterator<term_t>& end) {
        _id = begin != end ? (*begin)._term.id() :
            IdTraits<typename term_t::id_t>::default_value();

        _guts.clear();

        while (begin != end) {
            const term_t&           term((*begin)._term);
            Shard::packed_table_ptr table((*begin)._table);
            const char*             docid_base((*begin)._docid_base);
            docid_addresses().push_back(docid_base + term.doc_offset());
            doc_freqs().push_back(term.doc_freq());
            tables().push_back(table);
            ++begin;
        }
    }

    template <typename term_t>
    bool TermSeq<term_t>::operator==(const TermSeq& rhs) const {
        return
            _id               == rhs._id               &&
            docid_addresses() == rhs.docid_addresses() &&
            doc_freqs()       == rhs.doc_freqs()       &&
            tables()          == rhs.tables();
    }

    template <typename term_t>
    size_t TermSeq<term_t>::size() const {
        assert(docid_addresses().size() == doc_freqs().size());
        assert(docid_addresses().size() == tables().size());
        return docid_addresses().size();
    }

    template <typename term_t>
    std::string TermSeq<term_t>::to_string() const {
        std::ostringstream os;
        os << "[TermSeq id=" << id()
           << " size=" << size()
           << " addresses=";

        os << "[";
        for (auto addr: docid_addresses()) {
            os << " " << reinterpret_cast<const void*>(addr);
        }
        os << " ]";

        os << "]";
        return os.str();
    }

} // namespace imhotep

#endif
