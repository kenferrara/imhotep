#ifndef TERM_HPP
#define TERM_HPP

#include <stdint.h>
#include <iostream>
#include <string>

namespace imhotep {

    template <typename id_type>
    struct id_traits {
        static id_type default_value();
    };

    template <typename term_t>
    struct term_traits {
        static std::string  term_file_extension();
        static std::string docid_file_extension();
    };

    template <typename id_type>
    class Term {
        id_type _id         = id_traits<id_type>::default_value();
        int64_t _doc_offset = 0;
        int64_t _doc_freq   = 0;

    public:
        typedef id_type id_t;

        Term() = default;

        Term(const id_type& id, int64_t doc_offset, int64_t doc_freq)
            : _id(id)
            , _doc_offset(doc_offset)
            , _doc_freq(doc_freq)
        { }

        const id_type& id() const { return _id; }

        int64_t doc_offset() const { return _doc_offset; }
        int64_t   doc_freq() const { return _doc_freq;   }

        bool operator==(const Term& rhs) const { return id() == rhs.id(); }
        bool operator<(const Term& rhs)  const { return id() <  rhs.id(); }
    };

    typedef Term<int64_t>     IntTerm;
    typedef Term<std::string> StringTerm;

    template <>
    struct id_traits<int64_t> {
        static int64_t default_value() { return 0; }
    };

    template <>
    struct id_traits<std::string> {
        static std::string default_value() { return std::string(); }
    };

    template <>
    struct term_traits<IntTerm> {
        static std::string  term_file_extension() { return "intterms"; }
        static std::string docid_file_extension() { return "intdocs";  }
    };

    template <>
    struct term_traits<StringTerm> {
        static std::string  term_file_extension() { return "strterms"; }
        static std::string docid_file_extension() { return "strdocs";  }
    };

} // namespace imhotep


template<typename id_type>
std::ostream&
operator<<(std::ostream& os, const imhotep::Term<id_type>& term)
{
    os << "id: "          << term.id()
       << " doc_offset: " << term.doc_offset()
       << " doc_freq: "   << term.doc_freq();
    return os;
}

#endif