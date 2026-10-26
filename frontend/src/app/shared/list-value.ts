import { Resource, Signal, computed } from '@angular/core';

// value() wirft im Fehlerzustand, deshalb erst hasValue() prüfen
export function listValue<T>(resource: Resource<T[] | undefined>): Signal<T[]> {
  return computed(() => (resource.hasValue() ? resource.value() : []));
}
