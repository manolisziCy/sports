export const jwtDecode = (token) => {
  try {
    return JSON.parse(atob(token.split('.')[1]));
  } catch (e) {
    return null;
  }
};

export function deepCopy(object) {
  return JSON.parse(JSON.stringify(object));
}

export function parseFilters(filters) {
  let filterQuery = '';
  if (filters !== undefined && filters !== null && filters !== '') {
    for (let filter in filters) {
      const value =  filters[filter];
      if (value !== null && value !== '' && value !== undefined && !(value instanceof Array && value.length === 0)) {
        filterQuery += '&filter_' + filter + '=' + value;
      }
    }
  }
  return filterQuery;
}