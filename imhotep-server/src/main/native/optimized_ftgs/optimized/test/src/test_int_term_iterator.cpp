#include <algorithm>
#include <iostream>
#include <limits>

#include "shard.hpp"
#include "term_iterator.hpp"

using namespace std;
using namespace imhotep;

int main(int argc, char *argv[])
{
    if (argc != 3) {
        cerr << "usage: " << argv[0] << " <shard dir> <field name>" << endl;
    }

    const string shard_dir(argv[1]);
    const string field_name(argv[2]);

    IntTermIterator it(Shard::term_filename<IntTerm>(shard_dir, field_name));
    IntTermIterator end;
    while (it != end) {
        cout << *it << endl;
        ++it;
    }
}
