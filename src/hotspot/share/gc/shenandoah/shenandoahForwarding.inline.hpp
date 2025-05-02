/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP

#include "gc/shenandoah/shenandoahForwarding.hpp"

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "oops/klass.hpp"
#include "oops/markWord.hpp"
#include "runtime/javaThread.hpp"

inline oop ShenandoahForwarding::get_forwardee_raw(oop obj) {
  shenandoah_assert_in_heap_bounds(nullptr, obj);
  return get_forwardee_raw_unchecked(obj);
}

inline oop ShenandoahForwarding::get_forwardee_raw_unchecked(oop obj) {
  // JVMTI and JFR code use mark words for marking objects for their needs.
  // On this path, we can encounter the "marked" object, but with null
  // fwdptr. That object is still not forwarded, and we need to return
  // the object itself.
  markWord mark = obj->mark();
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    if (fwdptr != nullptr) {
      return cast_to_oop(fwdptr);
    }
  }
  return obj;
}

inline oop ShenandoahForwarding::get_forwardee_mutator(oop obj) {
  // Same as above, but mutator thread cannot ever see null forwardee.
  shenandoah_assert_correct(nullptr, obj);
  assert(Thread::current()->is_Java_thread(), "Must be a mutator thread");

  markWord mark = obj->mark();
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    assert(fwdptr != nullptr, "Forwarding pointer is never null here");
    return cast_to_oop(fwdptr);
  } else {
    return obj;
  }
}

inline oop ShenandoahForwarding::get_forwardee(oop obj) {
  shenandoah_assert_correct(nullptr, obj);
  return get_forwardee_raw_unchecked(obj);
}

inline bool ShenandoahForwarding::is_forwarded(markWord m) {
  return (m.value() & (markWord::lock_mask_in_place | markWord::self_fwd_mask_in_place)) > markWord::monitor_value;
}

inline bool ShenandoahForwarding::is_forwarded(oop obj) {
  return is_forwarded(obj->mark());
}

inline oop ShenandoahForwarding::try_update_forwardee(oop obj, oop update) {
  markWord old_mark = obj->mark();
  if (is_forwarded(old_mark)) {
    return cast_to_oop(old_mark.clear_lock_bits().to_pointer());
  }

  markWord new_mark = markWord::encode_pointer_as_mark(update);
  if (UseCompactObjectHeaders && old_mark.hash_is_hashed()) {
    new_mark = markWord(new_mark.value() | FWDED_HASH_TRANSITION);
  }
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_conservative);
  if (prev_mark == old_mark) {
    return update;
  } else {
    return cast_to_oop(prev_mark.clear_lock_bits().to_pointer());
  }
}

inline size_t ShenandoahForwarding::object_size(oop obj) {
  markWord mark = obj->mark();
  if (is_forwarded(mark)) {
    oop fwd = obj->forwardee(mark);
    markWord fwd_mark = fwd->mark();
    Klass* klass = UseCompactObjectHeaders ? fwd_mark.klass() : fwd->klass();
    size_t size = fwd->base_size_given_klass(klass);
    if (UseCompactObjectHeaders) {
      if ((mark.value() & FWDED_HASH_TRANSITION) != FWDED_HASH_TRANSITION) {
        if (fwd_mark.hash_is_copied() && klass->hash_requires_reallocation(fwd)) {
          // log_trace(gc)("Extended size for object: " PTR_FORMAT " base-size: " SIZE_FORMAT ", mark: " PTR_FORMAT, p2i(fwd), size, fwd_mark.value());
          size = align_object_size(size + 1);
        }
      }
    }
    return size;
  } else {
    Klass* klass = UseCompactObjectHeaders ? mark.klass() : obj->klass();
    return obj->size_given_mark_and_klass(mark, klass);
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
