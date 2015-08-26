#ifndef SPLITTER_HPP
#define SPLITTER_HPP

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <map>
#include <stdexcept>
#include <string>

#include "shard.hpp"
#include "term_iterator.hpp"

namespace imhotep {

    template <typename term_t> struct SplitterTraits { typedef void               iterator_t; };
    template <> struct SplitterTraits<IntTerm>       { typedef IntTermIterator    iterator_t; };
    template <> struct SplitterTraits<StringTerm>    { typedef StringTermIterator iterator_t; };

    template <typename term_t>
    class Splitter {
    public:
        typedef std::map<size_t, std::string> SplitNumToField;

        typedef typename SplitterTraits<term_t>::iterator_t term_iterator_t;

        Splitter(const Shard&       shard,
                 const std::string& field,
                 const std::string& splits_dir,
                 size_t num_splits);

        Splitter(const Shard&       shard,
                 const std::string& field,
                 term_iterator_t    term_iterator,
                 const std::string& splits_dir,
                 size_t num_splits);

        const Shard& shard() const { return _shard; }

        const std::string& splits_dir() const { return _splits_dir; }

        const SplitNumToField& splits() const { return _splits; } // !@# fix this name!

        void run();

    private:
        void write_string(FILE* file, const char* data, size_t length) const {
            if (fwrite(reinterpret_cast<const void*>(data),
                       sizeof(char), length, file) != length) {
                throw imhotep_error("write failed");
            }
        }

        template <typename T>
        void write_item(FILE* file, const T& item) const {
            if (fwrite(reinterpret_cast<const void*>(&item),
                       sizeof(T), 1, file) != 1) {
                throw imhotep_error("write failed");
            }
        }

        void encode(FILE* file, const term_t& term) const ;

        Shard           _shard;
        std::string     _splits_dir;
        std::string     _field;
        term_iterator_t _term_iterator;

        SplitNumToField _splits;
    };

    template <typename term_t>
    Splitter<term_t>::Splitter(const Shard&       shard,
                               const std::string& field,
                               const std::string& splits_dir,
                               size_t             num_splits)
        : _shard(shard)
        , _splits_dir(splits_dir)
        , _field(field)
        , _term_iterator(term_iterator_t(shard, field)) {
        for (size_t split_num(0); split_num < num_splits; ++split_num) {
            _splits[split_num] = field;
        }
    }

    template <typename term_t>
    Splitter<term_t>::Splitter(const Shard&       shard,
                               const std::string& field,
                               term_iterator_t    term_iterator,
                               const std::string& splits_dir,
                               size_t             num_splits)
        : _shard(shard)
        , _splits_dir(splits_dir)
        , _field(field)
        , _term_iterator(term_iterator)
    {
        for (size_t split_num(0); split_num < num_splits; ++split_num) {
            _splits[split_num] = field;
        }
    }

    template <typename term_t>
    void Splitter<term_t>::run()
    {
        std::vector<FILE*> split_files;
        for (SplitNumToField::const_iterator split_it(splits().begin());
             split_it != splits().end(); ++split_it) {
            const std::pair<size_t, std::string>& kv(*split_it);
            const std::string filename(_shard.split_filename(splits_dir(), kv.second, kv.first));
            FILE* file(fopen(filename.c_str(), "w"));
            if (!file) {
                throw imhotep_error("cannot create split file");
            }
            split_files.push_back(file);
        }

        term_iterator_t it(_term_iterator);
        term_iterator_t end;
        while (it != end) {
            const size_t  hash_val(it->hash());
            const size_t  split(hash_val % _splits.size());
            FILE*         file(split_files[split]);
            const term_t& term(*it);
            encode(file, term);
            ++it;
        }

        for (std::vector<FILE*>::iterator file_it(split_files.begin());
             file_it != split_files.end(); ++file_it) {
            if (fclose(*file_it) == -1) {
                throw imhotep_error("close() failed for split file"); // !@#
            }
        }
    }

} // namespace imhotep

#endif
